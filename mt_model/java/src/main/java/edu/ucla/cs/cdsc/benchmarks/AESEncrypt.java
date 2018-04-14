package edu.ucla.cs.cdsc.benchmarks;

public class AESEncrypt {
    private static final byte[] key = "Bar12345Bar12345".getBytes();

    private static final byte[] sbox = {
            (byte) 0x63, (byte) 0x7c, (byte) 0x77, (byte) 0x7b, (byte) 0xf2, (byte) 0x6b, (byte) 0x6f, (byte) 0xc5,
            (byte) 0x30, (byte) 0x01, (byte) 0x67, (byte) 0x2b, (byte) 0xfe, (byte) 0xd7, (byte) 0xab, (byte) 0x76,
            (byte) 0xca, (byte) 0x82, (byte) 0xc9, (byte) 0x7d, (byte) 0xfa, (byte) 0x59, (byte) 0x47, (byte) 0xf0,
            (byte) 0xad, (byte) 0xd4, (byte) 0xa2, (byte) 0xaf, (byte) 0x9c, (byte) 0xa4, (byte) 0x72, (byte) 0xc0,
            (byte) 0xb7, (byte) 0xfd, (byte) 0x93, (byte) 0x26, (byte) 0x36, (byte) 0x3f, (byte) 0xf7, (byte) 0xcc,
            (byte) 0x34, (byte) 0xa5, (byte) 0xe5, (byte) 0xf1, (byte) 0x71, (byte) 0xd8, (byte) 0x31, (byte) 0x15,
            (byte) 0x04, (byte) 0xc7, (byte) 0x23, (byte) 0xc3, (byte) 0x18, (byte) 0x96, (byte) 0x05, (byte) 0x9a,
            (byte) 0x07, (byte) 0x12, (byte) 0x80, (byte) 0xe2, (byte) 0xeb, (byte) 0x27, (byte) 0xb2, (byte) 0x75,
            (byte) 0x09, (byte) 0x83, (byte) 0x2c, (byte) 0x1a, (byte) 0x1b, (byte) 0x6e, (byte) 0x5a, (byte) 0xa0,
            (byte) 0x52, (byte) 0x3b, (byte) 0xd6, (byte) 0xb3, (byte) 0x29, (byte) 0xe3, (byte) 0x2f, (byte) 0x84,
            (byte) 0x53, (byte) 0xd1, (byte) 0x00, (byte) 0xed, (byte) 0x20, (byte) 0xfc, (byte) 0xb1, (byte) 0x5b,
            (byte) 0x6a, (byte) 0xcb, (byte) 0xbe, (byte) 0x39, (byte) 0x4a, (byte) 0x4c, (byte) 0x58, (byte) 0xcf,
            (byte) 0xd0, (byte) 0xef, (byte) 0xaa, (byte) 0xfb, (byte) 0x43, (byte) 0x4d, (byte) 0x33, (byte) 0x85,
            (byte) 0x45, (byte) 0xf9, (byte) 0x02, (byte) 0x7f, (byte) 0x50, (byte) 0x3c, (byte) 0x9f, (byte) 0xa8,
            (byte) 0x51, (byte) 0xa3, (byte) 0x40, (byte) 0x8f, (byte) 0x92, (byte) 0x9d, (byte) 0x38, (byte) 0xf5,
            (byte) 0xbc, (byte) 0xb6, (byte) 0xda, (byte) 0x21, (byte) 0x10, (byte) 0xff, (byte) 0xf3, (byte) 0xd2,
            (byte) 0xcd, (byte) 0x0c, (byte) 0x13, (byte) 0xec, (byte) 0x5f, (byte) 0x97, (byte) 0x44, (byte) 0x17,
            (byte) 0xc4, (byte) 0xa7, (byte) 0x7e, (byte) 0x3d, (byte) 0x64, (byte) 0x5d, (byte) 0x19, (byte) 0x73,
            (byte) 0x60, (byte) 0x81, (byte) 0x4f, (byte) 0xdc, (byte) 0x22, (byte) 0x2a, (byte) 0x90, (byte) 0x88,
            (byte) 0x46, (byte) 0xee, (byte) 0xb8, (byte) 0x14, (byte) 0xde, (byte) 0x5e, (byte) 0x0b, (byte) 0xdb,
            (byte) 0xe0, (byte) 0x32, (byte) 0x3a, (byte) 0x0a, (byte) 0x49, (byte) 0x06, (byte) 0x24, (byte) 0x5c,
            (byte) 0xc2, (byte) 0xd3, (byte) 0xac, (byte) 0x62, (byte) 0x91, (byte) 0x95, (byte) 0xe4, (byte) 0x79,
            (byte) 0xe7, (byte) 0xc8, (byte) 0x37, (byte) 0x6d, (byte) 0x8d, (byte) 0xd5, (byte) 0x4e, (byte) 0xa9,
            (byte) 0x6c, (byte) 0x56, (byte) 0xf4, (byte) 0xea, (byte) 0x65, (byte) 0x7a, (byte) 0xae, (byte) 0x08,
            (byte) 0xba, (byte) 0x78, (byte) 0x25, (byte) 0x2e, (byte) 0x1c, (byte) 0xa6, (byte) 0xb4, (byte) 0xc6,
            (byte) 0xe8, (byte) 0xdd, (byte) 0x74, (byte) 0x1f, (byte) 0x4b, (byte) 0xbd, (byte) 0x8b, (byte) 0x8a,
            (byte) 0x70, (byte) 0x3e, (byte) 0xb5, (byte) 0x66, (byte) 0x48, (byte) 0x03, (byte) 0xf6, (byte) 0x0e,
            (byte) 0x61, (byte) 0x35, (byte) 0x57, (byte) 0xb9, (byte) 0x86, (byte) 0xc1, (byte) 0x1d, (byte) 0x9e,
            (byte) 0xe1, (byte) 0xf8, (byte) 0x98, (byte) 0x11, (byte) 0x69, (byte) 0xd9, (byte) 0x8e, (byte) 0x94,
            (byte) 0x9b, (byte) 0x1e, (byte) 0x87, (byte) 0xe9, (byte) 0xce, (byte) 0x55, (byte) 0x28, (byte) 0xdf,
            (byte) 0x8c, (byte) 0xa1, (byte) 0x89, (byte) 0x0d, (byte) 0xbf, (byte) 0xe6, (byte) 0x42, (byte) 0x68,
            (byte) 0x41, (byte) 0x99, (byte) 0x2d, (byte) 0x0f, (byte) 0xb0, (byte) 0x54, (byte) 0xbb, (byte) 0x16
    };

    private static byte rj_sbox(byte x) {
        return sbox[x];
    }

    /* -------------------------------------------------------------------------- */
    private static byte rj_xtime(byte x)
    {
        return (x & 0x80) ? ((x << 1) ^ 0x1b) : (x << 1);
    } /* rj_xtime */

    /* -------------------------------------------------------------------------- */
    private static void aes_subBytes(byte[] buf)
    {
        byte i = 16;

        sub : while (i-- != 0) buf[i] = rj_sbox(buf[i]);
    } /* aes_subBytes */

    /* -------------------------------------------------------------------------- */
    private static void aes_addRoundKey(byte[] buf, byte[] key)
    {
        byte i = 16;

        addkey : while (i-- != 0) buf[i] ^= key[i];
    } /* aes_addRoundKey */

    /* -------------------------------------------------------------------------- */
    private static void aes_addRoundKey_cpy(byte[] buf, byte[] key, byte[] cpk) {
        byte i = 16;

        cpkey:
        while (i-- != 0) {
            buf[i] ^= (cpk[i] = key[i]);
            cpk[16 + i] = key[16 + i];
        }
    } /* aes_addRoundKey_cpy */

    /* -------------------------------------------------------------------------- */
    private static void aes_shiftRows(byte[] buf)
    {
        byte i, j; /* to make it potentially parallelable :) */

        i = buf[1]; buf[1] = buf[5]; buf[5] = buf[9]; buf[9] = buf[13]; buf[13] = i;
        i = buf[10]; buf[10] = buf[2]; buf[2] = i;
        j = buf[3]; buf[3] = buf[15]; buf[15] = buf[11]; buf[11] = buf[7]; buf[7] = j;
        j = buf[14]; buf[14] = buf[6]; buf[6]  = j;

    } /* aes_shiftRows */

    /* -------------------------------------------------------------------------- */
    private static void aes_mixColumns(byte[] buf)
    {
        byte i, a, b, c, d, e;

        mix : for (i = 0; i < 16; i += 4)
        {
            a = buf[i]; b = buf[i + 1]; c = buf[i + 2]; d = buf[i + 3];
            e = (byte) (a ^ b ^ c ^ d);
            buf[i] ^= e ^ rj_xtime((byte) (a^b));
            buf[i+1] ^= e ^ rj_xtime((byte) (b^c));
            buf[i+2] ^= e ^ rj_xtime((byte) (c^d));
            buf[i+3] ^= e ^ rj_xtime((byte) (d^a));
        }
    } /* aes_mixColumns */

    private static byte F(byte x) {
        return (byte) (((x)<<1) ^ ((((x)>>7) & 1) * 0x1b));
    }

    /* -------------------------------------------------------------------------- */
    private static void aes_expandEncKey(byte[] k, byte[] rc)
    {
        byte i;

        k[0] ^= rj_sbox(k[29]) ^ (rc[0]);
        k[1] ^= rj_sbox(k[30]);
        k[2] ^= rj_sbox(k[31]);
        k[3] ^= rj_sbox(k[28]);
        rc[0] = F(rc[0]);

        exp1 : for(i = 4; i < 16; i += 4)  {
            k[i] ^= k[i-4];   k[i+1] ^= k[i-3];
            k[i+2] ^= k[i-2]; k[i+3] ^= k[i-1];
        }
        k[16] ^= rj_sbox(k[12]);
        k[17] ^= rj_sbox(k[13]);
        k[18] ^= rj_sbox(k[14]);
        k[19] ^= rj_sbox(k[15]);

        exp2 : for(i = 20; i < 32; i += 4) {
            k[i] ^= k[i-4];   k[i+1] ^= k[i-3];
            k[i+2] ^= k[i-2]; k[i+3] ^= k[i-1];
        }
    } /* aes_expandEncKey */

    /* -------------------------------------------------------------------------- */
    private static void aes256_encrypt_ecb(byte[] k, byte[] buf)
    {
        //INIT
        byte[] rcon = new byte[1];
        rcon[0] = 1
        byte i;

        byte[] enckey = new byte[32];
        byte[] deckey = new byte[32];
        byte[] key = new byte[32];

        ecb1 : for (i = 0; i < 32; i++){
            enckey[i] = deckey[i] = k[i];
        }
        ecb2 : for (i = 8; --i != 0; ){
            aes_expandEncKey(deckey, &rcon);
        }

        //DEC
        aes_addRoundKey_cpy(buf, enckey, key);
        ecb3 : for(i = 1, rcon[0] = 1; i < 14; ++i)
        {
            aes_subBytes(buf);
            aes_shiftRows(buf);
            aes_mixColumns(buf);
            if( (i & 1) != 0) aes_addRoundKey( buf, key);
            else {
                aes_expandEncKey(key, rcon);
                aes_addRoundKey(buf, key);
            }
        }
        aes_subBytes(buf);
        aes_shiftRows(buf);
        aes_expandEncKey(key, rcon);
        aes_addRoundKey(buf, key);
    } /* aes256_encrypt */


    public static byte[] encrypt(byte[] data) {
        byte[] result = new byte[data.length];
        byte[] piece = new byte[16];
        for (int i=0; i<data.length/16; i++) {
            for (int j=0; j<16; j++) piece[j] = data[i*16+j];
            aes256_encrypt_ecb(key, piece);
            for (int j=0; j<16; j++) result[i*16+j] = piece[j];
        }
        return result;
    }
}
