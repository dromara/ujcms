import { type CipherMode, sm2 } from 'sm-crypto';

/**
 * Hex → Base64（标准字母表、带 '=' 填充）。原由 crypto-js 的 Base64.stringify(Hex.parse(hex)) 完成，
 * crypto-js 已停止维护且此处只用到编码转换，故改为内联实现，结果逐字节一致。
 */
const hexToBase64 = (hex: string): string => {
  let binary = '';
  for (let i = 0; i < hex.length; i += 2) {
    binary += String.fromCharCode(parseInt(hex.substring(i, i + 2), 16));
  }
  return btoa(binary);
};

/** Base64 → 小写 Hex。与原 crypto-js 的 Hex.stringify(Base64.parse(base64)) 结果一致 */
const base64ToHex = (base64: string): string => {
  const binary = atob(base64);
  let hex = '';
  for (let i = 0; i < binary.length; i += 1) {
    hex += binary.charCodeAt(i).toString(16).padStart(2, '0');
  }
  return hex;
};

/**
 * SM2 加密。后台Java使用BC库解密，必须在JS库加密后的Hex值前加上'04'，且转为Base64编码格式，减小数据传输量。
 * @param msg 待加密的信息
 * @param publicKey 公钥。QD值，Hex编码。
 * @param cipherMode 模式。1: C1C3C2, 0: C1C2C3。默认 1
 * @returns 加密后的字符串。Base64编码。
 */
export const sm2Encrypt = (msg: string, publicKey: string, cipherMode?: CipherMode): string => hexToBase64('04' + sm2.doEncrypt(msg, publicKey, cipherMode));

/**
 * SM2 解密。后台Java使用BC库加密，必须将Base64编码转为Hex编码，然后去掉前面'04'字符。
 * @param encryptData 待解密的字符串。Base64编码。
 * @param privateKey 私钥。QD值，Hex编码。
 * @param cipherMode 模式。1: C1C3C2, 0: C1C2C3。默认 1
 * @returns 解密后的字符串。
 */
export const sm2Decrypt = (encryptData: string, privateKey: string, cipherMode?: CipherMode): string => {
  // 去除前面两位'04'字符
  const data = base64ToHex(encryptData).substring(2);
  return sm2.doDecrypt(data, privateKey, cipherMode);
};

/**
 * SM2 签名。加上 { hash:true, der:true } 参数，与后台Java BC库默认签名一致。
 * @param msg 待签名信息
 * @param privateKey 私钥
 * @returns 签名。Hex编码
 */
export const sm2Signature = (msg: string, privateKey: string): string => sm2.doSignature(msg, privateKey, { hash: true, der: true });

/**
 * SM2 验签。加上 { hash:true, der:true } 参数，与后台Java BC库默认验签一致。
 * @param msg 待验证信息
 * @param signHex 签名。Hex编码。
 * @param publicKey 公钥
 * @returns 是否验签成功
 */
export const sm2VerifySignature = (msg: string, signHex: string, publicKey: string): boolean => sm2.doVerifySignature(msg, signHex, publicKey, { hash: true, der: true });
