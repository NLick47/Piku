use ring::{aead, agreement, hkdf, hmac, rand};
use rustls::crypto::hpke::{
    EncapsulatedSecret, Hpke, HpkeOpener, HpkePrivateKey, HpkePublicKey, HpkeSealer, HpkeSuite,
};
use rustls::internal::msgs::enums::{HpkeAead, HpkeKdf, HpkeKem};
use rustls::internal::msgs::handshake::HpkeSymmetricCipherSuite;
use rustls::Error;

const KEM_ID: u16 = 0x0020;
const KDF_ID: u16 = 0x0001;
const AEAD_ID: u16 = 0x0001;

const NK: usize = 16;
const NN: usize = 12;
const N_SECRET: usize = 32;

/// 交给 `rustls::client::EchConfig::new` 的套件表（这份实现只认这一套）
pub static ECH_HPKE_SUITE: &[&dyn Hpke] = &[&X25519HkdfSha256Aes128Gcm];

#[derive(Debug)]
struct X25519HkdfSha256Aes128Gcm;

impl Hpke for X25519HkdfSha256Aes128Gcm {
    fn seal(
        &self,
        info: &[u8],
        aad: &[u8],
        plaintext: &[u8],
        pub_key: &HpkePublicKey,
    ) -> Result<(EncapsulatedSecret, Vec<u8>), Error> {
        let (enc, mut sealer) = self.setup_sealer(info, pub_key)?;
        Ok((enc, sealer.seal(aad, plaintext)?))
    }

    fn setup_sealer(
        &self,
        info: &[u8],
        pub_key: &HpkePublicKey,
    ) -> Result<(EncapsulatedSecret, Box<dyn HpkeSealer + 'static>), Error> {
        let rng = rand::SystemRandom::new();
        let (shared_secret, enc) = encap(&rng, &pub_key.0)?;
        let ctx = key_schedule(&shared_secret, info)?;
        Ok((
            EncapsulatedSecret(enc),
            Box::new(Sealer {
                key: ctx.key,
                base_nonce: ctx.base_nonce,
                seq: 0,
            }),
        ))
    }

    // 只做客户端 ECH，收侧与 grease 用不到
    fn open(
        &self,
        _enc: &EncapsulatedSecret,
        _info: &[u8],
        _aad: &[u8],
        _ciphertext: &[u8],
        _secret_key: &HpkePrivateKey,
    ) -> Result<Vec<u8>, Error> {
        Err(unsupported())
    }

    fn setup_opener(
        &self,
        _enc: &EncapsulatedSecret,
        _info: &[u8],
        _secret_key: &HpkePrivateKey,
    ) -> Result<Box<dyn HpkeOpener + 'static>, Error> {
        Err(unsupported())
    }

    fn generate_key_pair(&self) -> Result<(HpkePublicKey, HpkePrivateKey), Error> {
        Err(unsupported())
    }

    fn suite(&self) -> HpkeSuite {
        HpkeSuite {
            kem: HpkeKem::DHKEM_X25519_HKDF_SHA256,
            sym: HpkeSymmetricCipherSuite {
                kdf_id: HpkeKdf::HKDF_SHA256,
                aead_id: HpkeAead::AES_128_GCM,
            },
        }
    }
}

fn unsupported() -> Error {
    Error::General("piku-ech: 只实现客户端 ECH 需要的 HPKE 单向封装".into())
}

#[derive(Debug)]
struct Sealer {
    key: [u8; NK],
    base_nonce: [u8; NN],
    seq: u64,
}

impl HpkeSealer for Sealer {
    fn seal(&mut self, aad: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, Error> {
        let nonce = seq_nonce(&self.base_nonce, self.seq);
        self.seq += 1;
        let key = aead::LessSafeKey::new(
            aead::UnboundKey::new(&aead::AES_128_GCM, &self.key)
                .map_err(|_| Error::General("piku-ech: aes key".into()))?,
        );
        let mut out = plaintext.to_vec();
        key.seal_in_place_append_tag(
            aead::Nonce::assume_unique_for_key(nonce),
            aead::Aad::from(aad),
            &mut out,
        )
        .map_err(|_| Error::General("piku-ech: hpke seal".into()))?;
        Ok(out)
    }
}

/// RFC 9180 §5.1.1 Encap：生成临时密钥、做 DH、得到 shared_secret
fn encap(rng: &rand::SystemRandom, pk_r: &[u8]) -> Result<(Vec<u8>, Vec<u8>), Error> {
    if pk_r.len() != 32 {
        return Err(Error::General("piku-ech: 不是 X25519 公钥".into()));
    }
    let sk_e = agreement::EphemeralPrivateKey::generate(&agreement::X25519, rng)
        .map_err(|_| Error::General("piku-ech: 生成临时密钥失败".into()))?;
    let pk_e = sk_e
        .compute_public_key()
        .map_err(|_| Error::General("piku-ech: 导出临时公钥失败".into()))?
        .as_ref()
        .to_vec();
    let peer = agreement::UnparsedPublicKey::new(&agreement::X25519, pk_r);
    let agreed = agreement::agree_ephemeral(sk_e, &peer, |secret| {
        Ok::<Vec<u8>, Error>(secret.to_vec())
    })
    .map_err(|_| Error::General("piku-ech: X25519 协商失败".into()))?;
    let dh = agreed.map_err(|_| Error::General("piku-ech: X25519 协商失败".into()))?;
    Ok((kem_shared_secret(&dh, &pk_e, pk_r)?, pk_e))
}

/// DH 之后的派生：eae_prk → shared_secret
fn kem_shared_secret(dh: &[u8], enc: &[u8], pk_r: &[u8]) -> Result<Vec<u8>, Error> {
    let mut kem_context = Vec::with_capacity(enc.len() + pk_r.len());
    kem_context.extend_from_slice(enc);
    kem_context.extend_from_slice(pk_r);
    let suite = kem_suite_id();
    let eae_prk = labeled_extract(&[], &suite, b"eae_prk", dh)?;
    labeled_expand(&eae_prk, &suite, b"shared_secret", &kem_context, N_SECRET)
}

struct KeySchedule {
    key: [u8; NK],
    base_nonce: [u8; NN],
}

/// RFC 9180 §5.1 SetupBaseS 的密钥表（base mode，psk 为空）
fn key_schedule(shared_secret: &[u8], info: &[u8]) -> Result<KeySchedule, Error> {
    let suite = hpke_suite_id();
    let psk_id_hash = labeled_extract(&[], &suite, b"psk_id_hash", &[])?;
    let info_hash = labeled_extract(&[], &suite, b"info_hash", info)?;
    let mut context = Vec::with_capacity(1 + 32 + 32);
    context.push(0x00); // mode_base
    context.extend_from_slice(&psk_id_hash);
    context.extend_from_slice(&info_hash);

    let secret = labeled_extract(shared_secret, &suite, b"secret", &[])?;
    Ok(KeySchedule {
        key: to_array(labeled_expand(&secret, &suite, b"key", &context, NK)?)?,
        base_nonce: to_array(labeled_expand(&secret, &suite, b"base_nonce", &context, NN)?)?,
    })
}

fn to_array<const N: usize>(bytes: Vec<u8>) -> Result<[u8; N], Error> {
    bytes
        .try_into()
        .map_err(|_| Error::General("piku-ech: 长度不对".into()))
}

fn kem_suite_id() -> [u8; 5] {
    let mut id = [0u8; 5];
    id[..3].copy_from_slice(b"KEM");
    id[3..].copy_from_slice(&KEM_ID.to_be_bytes());
    id
}

fn hpke_suite_id() -> [u8; 10] {
    let mut id = [0u8; 10];
    id[..4].copy_from_slice(b"HPKE");
    id[4..6].copy_from_slice(&KEM_ID.to_be_bytes());
    id[6..8].copy_from_slice(&KDF_ID.to_be_bytes());
    id[8..].copy_from_slice(&AEAD_ID.to_be_bytes());
    id
}

fn labeled_extract(salt: &[u8], suite_id: &[u8], label: &[u8], ikm: &[u8]) -> Result<Vec<u8>, Error> {
    let mut labeled_ikm = Vec::with_capacity(7 + suite_id.len() + label.len() + ikm.len());
    labeled_ikm.extend_from_slice(b"HPKE-v1");
    labeled_ikm.extend_from_slice(suite_id);
    labeled_ikm.extend_from_slice(label);
    labeled_ikm.extend_from_slice(ikm);
    // 用 HMAC 直接做 HKDF-Extract：ring 的 hkdf::Prk 不吐原始字节，而
    // key_schedule_context 需要 psk_id_hash / info_hash 的原值
    let salt = if salt.is_empty() { vec![0u8; 32] } else { salt.to_vec() };
    let key = hmac::Key::new(hmac::HMAC_SHA256, &salt);
    Ok(hmac::sign(&key, &labeled_ikm).as_ref().to_vec())
}

fn labeled_expand(
    prk: &[u8],
    suite_id: &[u8],
    label: &[u8],
    info: &[u8],
    len: usize,
) -> Result<Vec<u8>, Error> {
    // RFC 9180 §4.1：labeled_info = I2OSP(L, 2) || "HPKE-v1" || suite_id || label || info
    let mut labeled_info = Vec::with_capacity(9 + suite_id.len() + label.len() + info.len());
    labeled_info.extend_from_slice(&(len as u16).to_be_bytes());
    labeled_info.extend_from_slice(b"HPKE-v1");
    labeled_info.extend_from_slice(suite_id);
    labeled_info.extend_from_slice(label);
    labeled_info.extend_from_slice(info);
    let prk = hkdf::Prk::new_less_safe(hkdf::HKDF_SHA256, prk);
    let info_slices = [labeled_info.as_slice()];
    let okm = prk
        .expand(&info_slices, HkdfLen(len))
        .map_err(|_| Error::General("piku-ech: hkdf expand 失败".into()))?;
    let mut out = vec![0u8; len];
    okm.fill(&mut out)
        .map_err(|_| Error::General("piku-ech: hkdf 取输出失败".into()))?;
    Ok(out)
}

struct HkdfLen(usize);

impl hkdf::KeyType for HkdfLen {
    fn len(&self) -> usize {
        self.0
    }
}

fn seq_nonce(base: &[u8; NN], seq: u64) -> [u8; NN] {
    let mut nonce = *base;
    for (i, b) in seq.to_be_bytes().iter().enumerate() {
        nonce[NN - 8 + i] ^= b;
    }
    nonce
}

#[cfg(test)]
mod tests {
    use super::*;

    // RFC 9180 A.1：DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-128-GCM, base mode
    const SK_EM: &str = "52c4a758a802cd8b936eceea314432798d5baf2d7e9235dc084ab1b9cfa2f736";
    const PK_EM: &str = "37fda3567bdbd628e88668c3c8d7e97d1d1253b6d4ea6d44c150f741f1bf4431";
    const PK_RM: &str = "3948cfe0ad1ddb695d780e59077195da6c56506b027329794ab02bca80815c4d";
    const INFO: &str = "4f6465206f6e2061204772656369616e2055726e";
    const SHARED_SECRET: &str = "fe0e18c9f024ce43799ae393c7e8fe8fce9d218875e8227b0187c04e7d2ea1fc";
    const KEY: &str = "4531685d41d65f03dc48f6b8302c05b0";
    const BASE_NONCE: &str = "56d890e5accaaf011cff4b7d";
    const PT: &str = "4265617574792069732074727574682c20747275746820626561757479";

    fn hex(s: &str) -> Vec<u8> {
        (0..s.len())
            .step_by(2)
            .map(|i| u8::from_str_radix(&s[i..i + 2], 16).unwrap())
            .collect()
    }

    #[test]
    fn suite_reports_what_cloudflare_offers() {
        // 云上那份 ECH 配置写的是 kem=0x0020 kdf=0x0001 aead=0x0001，对不上就静默回落成明文
        let suite = X25519HkdfSha256Aes128Gcm.suite();
        assert_eq!(HpkeKem::DHKEM_X25519_HKDF_SHA256, suite.kem);
        assert_eq!(HpkeKdf::HKDF_SHA256, suite.sym.kdf_id);
        assert_eq!(HpkeAead::AES_128_GCM, suite.sym.aead_id);
    }

    #[test]
    fn kem_shared_secret_matches_rfc9180_a1() {
        let dh = x25519_dalek::x25519(
            hex(SK_EM).try_into().unwrap(),
            hex(PK_RM).try_into().unwrap(),
        );
        let shared = kem_shared_secret(&dh, &hex(PK_EM), &hex(PK_RM)).unwrap();
        assert_eq!(hex(SHARED_SECRET), shared);
    }

    #[test]
    fn key_schedule_matches_rfc9180_a1() {
        let ctx = key_schedule(&hex(SHARED_SECRET), &hex(INFO)).unwrap();
        assert_eq!(hex(KEY), ctx.key.to_vec(), "key 与官方向量不一致");
        assert_eq!(hex(BASE_NONCE), ctx.base_nonce.to_vec(), "base_nonce 不一致");
    }

    #[test]
    fn sealing_matches_rfc9180_a1_1_1() {
        let pt = hex(PT);
        let cases = [
            (0u64, "436f756e742d30", "f938558b5d72f1a23810b4be2ab4f84331acc02fc97babc53a52ae8218a355a96d8770ac83d07bea87e13c512a"),
            (1, "436f756e742d31", "af2d7e9ac9ae7e270f46ba1f975be53c09f8d875bdc8535458c2494e8a6eab251c03d0c22a56b8ca42c2063b84"),
            (2, "436f756e742d32", "498dfcabd92e8acedc281e85af1cb4e3e31c7dc394a1ca20e173cb72516491588d96a19ad4a683518973dcc180"),
            (255, "436f756e742d323535", "7175db9717964058640a3a11fb9007941a5d1757fda1a6935c805c21af32505bf106deefec4a49ac38d71c9e0a"),
            (256, "436f756e742d323536", "957f9800542b0b8891badb026d79cc54597cb2d225b54c00c5238c25d05c30e3fbeda97d2e0e1aba483a2df9f2"),
        ];
        for (seq, aad, expected) in cases {
            let mut sealer = Sealer {
                key: to_array(hex(KEY)).unwrap(),
                base_nonce: to_array(hex(BASE_NONCE)).unwrap(),
                seq,
            };
            let ct = sealer.seal(&hex(aad), &pt).unwrap();
            assert_eq!(hex(expected), ct, "序号 {seq} 的密文与官方向量不一致");
        }
    }

    #[test]
    fn nonce_is_base_nonce_xor_seq() {
        let base: [u8; NN] = to_array(hex(BASE_NONCE)).unwrap();
        assert_eq!(hex("56d890e5accaaf011cff4b7d"), seq_nonce(&base, 0).to_vec());
        assert_eq!(hex("56d890e5accaaf011cff4b7c"), seq_nonce(&base, 1).to_vec());
        assert_eq!(hex("56d890e5accaaf011cff4b82"), seq_nonce(&base, 255).to_vec());
        assert_eq!(hex("56d890e5accaaf011cff4a7d"), seq_nonce(&base, 256).to_vec());
    }
}
