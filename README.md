# Takamaka.io Messages

The examples are provided with prettified json, in the real application the 
jsons have to be minimized.

## External Json Envelope

All jsons with the same major version (e.g., 1.X) maintain backward 
compatibility; a parser created to read version 1.0 will be able to decode 
version 1.1 simply by ignoring fields not defined in the version. 
In the event that a field is introduced that breaks compatibility a new major 
version must be created. The json version corresponds to the field with the 
highest version number.

- **[version](#External-Json-Envelope)**, key:"v", string like "1.0"
- **[action](#Action)**, key: "a", the data needed to perform the action
    - *[message of action](#Message-Of-Action)*
        - **[fr](#From)**, key: "fr", from, the sender address in [Address](#Address) format
            - [t](#Address-Type) type
            - [ma](#Address-String) string
        - [to](#To) key: "to", to, the reciever address in [Address](#Address) format
            - [t](#Address-Type), key: "t", type
            - [ma](#Address-String), key: "ma", message address
        - [dt](#Date), key: "dt", date, unix time stamp millis
        - [g](#Green), key: "g", green, TKG value in nanoTK
        - [r](#Red), key: "r", red, value in nanoTK
        - [tm](#Text-Message), key: "tm", text message, UTF8 Text Message
        - [ew](#Encoded-Wallet), key: "ew", encoded wallet, Wallet encrypted with password
- **[type](#Type)**, key: "t", what action I expect to be performed by the qr
    - *[Values For Type](#Values-For-Type)*
        - *[st](#Stake-To-Node), value: "st"*, stake to node (v1.0)
        - *[b](#Blob), value: "b"*, blob (v1.0)
        - *[rp](#Request-Pay), value: "rp"*, request pay (v1.0)
        - *[st](#Stake-To-Node), value: "st"*, stake to node (v1.0)
        - *[su](#Steke-Undo), value: "su"*, stake undo (v1.0)
        - *[we](#Wallet-Encrypted), value: "ew"*, wallet encrypted (v1.0)
- **[Type Of Signature](#Type-Of-Signature)**, key:"ts", string like "Ed25519BC" defined by the takamaka.io core wallet library
- **[Signature](#Signature)**, key:"sg", base64 url encoded bytes of the signature
- **[Encryption](#Encryption)**, key:"ea", encrypted message action

## Action

key **a**

### Message Of Action

#### Date

key **dt**

a timestamp when required by the specific function, the timestamp is of type 
unix in milliseconds (e.g. 2024-10-17 8:01:51 GMT &rarr; 1729152111 )

#### Green

key **g**

TKG value in nanoTKG (e.g. 10 000 000 000 nanoTKG &rarr; 10 TKG)

#### Red

key **r**

TKR value in nanoTKG (e.g. 2 000 000 000 nanoTKG &rarr; 2 TKG)

#### Address

keys **fr** or **to**

If it is an ed25519 the base64 URL encoding, if it is a qTesla the sha3-384 
encoded base64 URL. If it is an unrecognized object (e.g., an incorrect "to" 
field in which a string has been entered that does not fall into the previous 
cases) the sha3-384 encoded base64 URL of the same. If the address is in 
bas64url format and is 64 characters long it is considered as a compact address 
and used directly.

##### Address Type

key **t**

 - **f** full address
 - **c** compact sha3-384 encoded base64 URL


##### Address String

key **ma** message address

Base64 URL encoded byte string with "." has padding char

#### Text Message

key **tm**

A UTF8 free text message, for use within QRs it is advisable to keep under 
150 characters otherwise the QR becomes too large and loses readability 
by devices.

#### Encoded Wallet

key **ew**

A special transaction that allows the password-protected version of the wallet 
to be exported. The security of this transaction depends on how strong 
the password is. There is no significant difference in size between encryption 
types because what is exported is the seed generator for entropy and 
not the key.

The format of the content of the "ew" clause is defined by the 
takamaka.io core wallet library.

```json
{
    "version" : "0.1",
    "algorithm" : "AES",
    "wallet" : [ "uzEj+z98xCe38GfXDrVupQ==", "bpc ...[OMISSIS]... UQDL" ]
}
```

#### From

key **fr**

Source Address of type [Address](#Address)

#### To

key **to**

Destination address of type [Address](#Address)

## Type

key **rp**

The type of request action that the Json or QR includes. This field is 
mandatory.

### Values For Type

#### Stake To Node

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/STAKE.png)

```json
{
  "v" : "1.0",
  "a" : {
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10,
    "tm" : "test stake request"
  },
  "t" : "rp"
}
```

#### Blob

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/BLOB.png)

```json
{
  "v" : "1.0",
  "a" : {
    "tm" : "this is a message to be loaded in blockchain"
  },
  "t" : "b"
}
```

#### Pay

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/PAY.png)

```json
{
  "v" : "1.0",
  "a" : {
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10000000000,
    "r" : 2000000000,
    "tm" : "chiave qTesla + green + red"
  },
  "t" : "rp"
}
```

#### Stake Undo

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/STAKE_UNDO.png)

```json
{
  "v" : "1.0",
  "a" : {
    "dt" : 1729099034727,
    "tm" : "test stake undo request"
  },
  "t" : "rp"
}
```

#### Wallet Encrypted

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/WALLET_ENC.png)

```json
{
  "v" : "1.0",
  "a" : {
    "ew" : {
      "version" : "0.1",
      "algorithm" : "AES",
      "wallet" : [ "+DtiaqI+/xyAE7a/PCNW7w==", "AonDNdZsJFlbHznjAlPgufpL+OwnfMbPwJTOLqcFB+ZaxlB9O21SBn7xF11ah1UNXS4JtsTjfsbkXB2+NZsTPyYohbQmDp3iqSrkV2qPEPZkn6iU8+lbk93tvOubObgvNiYFY+9FhPWUFWi1tW8jbp0zzTF51vRGuDTTcaNjh7xYDu0VgsAhr4ogsg/bR97b2my6V5ulSW404f76bh9NUbEadcFS0zlyQr3C2tLmaFbTdRVd8XKA0Yn4iltUZF5SrppkS8eY+bYRr3sxstkpGZkeEl5ajsoBv5rSVMbn4SuN9l5sJZeoa8yr9RmZ7UKHRP9FdjaoVtsQfwioawxDBXcPoRcO96Kf0mB4lrkLdkpn9c8zhSfpwZ+AIAUkPHRurdufFFG3C8TaEpv+6SoeecNNuAGxOg4oQT1myabZqHSs9ei6PGzQpTIRuDzvLp5vj1kkghUvs265Tsl7LQ4W4sb859oxgIHqRV/IV682gdg=" ]
    }
  },
  "t" : "we"
}
```

## Type Of Signature

key **ts**

Defined by the takamaka.io core wallet library

```java
package io.takamaka.wallet.utils;

[...]

    public final class KeyContexts {

    [...]

        public final class KeyContexts {

        [...]

        public static enum WalletCypher {

            /**
             * currently not in use
             */
            Ed25519,
            /**
             * currently not in use
             */
            Tink,
            /**
             * current takamaka ed implementation
             */
            Ed25519BC,
            /**
             * bouncy castle, provable secure, I, Round 1
             */
            BCQTESLA_PS_1,
            /**
             * bouncy castle, provable secure, I, Round 2
             */
            BCQTESLA_PS_1_R2,
            /**
            * not for signature, key exchange
            */
            Curve25519BC

        }

        [...]

        }

    [...]

    }

```

## Signature

key **sg**

Base64 URL encoded signature.

Signed messages are a modification of normal messages where:

 - the from field contains the public key or its compact version
 - the ts field contains the signature type
 - the sg field contains the signature

### Signed Pay Example

![image](https://github.com/takamaka-dev/messages/blob/master/src/main/resources/img/SIGNED_PAY.png)

```json
{
  "v" : "1.0",
  "a" : {
    "fr" : {
      "t" : "f",
      "ma" : "v8a3bHFvpKadNvBEYGhstAW3hFQ9YTonsClrSML_T_4."
    },
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10000000000,
    "r" : 2000000000,
    "tm" : "chiave qTesla + green + red"
  },
  "t" : "rp",
  "ts" : "Ed25519BC",
  "sg" : "XdtrpA_fI1-rDgvvxwDB7jOg5PZV1-oTrBGRNLyatXjiPBlfkicVLsh5uxFsLCRahlvdhzyda7F40VwP_AX7Bg.."
}
```

### Sign Details

The signature covers only the **[a](#Action)** bean of the message. The value of the top-level key **[a](#Action)** must be taken to generate the message to be signed/verified. This json should be sorted by keys in its most compact form, without spaces or indentations.
If you are using Jackson as a serializer these are the `ObjectMapper` options:

```java
ObjectMapper mapper = new ObjectMapper();
mapper.configure(SerializationFeature.INDENT_OUTPUT, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsString(messageAction);
```


Where `messageAction` is the action bean (value of key **a**).

The external bean coming from the QR (or another message):

```json
{
  "v" : "1.0",
  "a" : {
    "fr" : {
      "t" : "f",
      "ma" : "v8a3bHFvpKadNvBEYGhstAW3hFQ9YTonsClrSML_T_4."
    },
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10000000000,
    "r" : 2000000000,
    "tm" : "key qTesla + green + red"
  },
  "t" : "rp",
  "ts" : "ed25519BC",
  "sg" : "XdtrpA_fI1-rDgvvxwDB7jOg5PZV1-oTrBGRNLyatXjiPBlfkicVLsh5uxFsLCRahlvdhzyda7F40VwP_AX7Bg.."
}
```

The object to be signed (prettified for convenience, not the actual signed 
message).

```json
{
    "fr" : {
      "t" : "f",
      "ma" : "v8a3bHFvpKadNvBEYGhstAW3hFQ9YTonsClrSML_T_4."
    },
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10000000000,
    "r" : 2000000000,
    "tm" : "key qTesla + green + red"
}
```

This object should be passed to the minimized signature function.

```json
{"fr":{"t": "f", "ma": "v8a3bHFvpKadNvBEYGhstAW3hFQ9YTonsClrSML_T_4. "},"to":{"t":"c","ma":"Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"},"g":10000000000,"r":2000000000,"tm":"chiave qTesla + green + red"}
```
the spaces present are those of the original free text message, which should be 
left unchanged, if it is a json in turn the stringified version of the value 
of **[tm](#Text-Message)** should be passed.

In case you are sending a signed message this field contains the public key with
 which the message is signed.
If the public key is of type *c* compact, the original key must be retrieved by 
an external function (e.g., via bookmarks). This is because it is a hash and it 
is not possible to reconstruct the original address.

If a qTesla key is used for signing, the message will probably be too large for 
a QR; the use of post-quantum encryption is intended for other communication 
channels that do not have such stringent limitations on the number of characters 
as the QR. In case you decide to use it you will also need to pass the signature 
as a hash (a post-quantum type hash such as sha3-384 or higher) but in the 
current version the signature distribution channel has not yet been put 
implemented.

The resulting signature should be entered as the value of the 
**[sg](#Signature)** field in base64 url format with "." as padding.

In the previous example:

```json
"sg" : "XdtrpA_fI1-rDgvvxwDB7jOg5PZV1-oTrBGRNLyatXjiPBlfkicVLsh5uxFsLCRahlvdhzyda7F40VwP_AX7Bg.."
```

#### Sign Using Takamaka Library

Example of signature construction

##### Get a base message

```java
BaseBean simplePayRequest_v0 = SimpleRequestModels.getSimplePayRequest_V_1_0("8NOhz0b58Y1Ri00m1i2S0xo2kfUuAdkKuHu1N4DSyAaJSrsUqgmACcRw3gD_ID4LGkPfB6Okq2RhNxRY_sAgoQYRp3sNnkwdJpqdDZlJn1XOxMUzB9WWF1e5iB_QTI7zvcnTNGKY8d59jAIjmkwyturI2Ytm10Rn1WaO2UHyDe9vRrTv9MZMifYlmvUG_M2uhzhoc-o9fhj68YPEPOSUIUGes0hw40T2Pg5L6zKjofzG51XqOs18Mp3qNFSFMb0IQUp-QJ3hSDBlCXHtiGMwq3P0iM-EkGXtjgECb1Ao4dje2trE01she5k9hOkZJz_8k2qQTpxNAWKp81D-7qSbIQhhvdyDfc9nBp2fhnUuRtCBaRlC1uAF7Qxp4EMxjWz9XCAlE4yQxogKTVC5OidcPNiXakka7egAVH4fkXO1Pdb28lPqMM0t630xrZEIxS3kH7mPZktsew5bsNo-Qgfu8BnrKHhlrStMqj5VUwoCr_J3TZmrRSyiPgukXRRPWwQCCQYeLIgqNASh3pIU2gioVTnt5MxeO8Dm4MX1sXKcz_UIXMOwxZFeB95Yu-YWUZxky3yLKW2lAbtWCUVylQwNYxxAg3bAmK12mqbTMzAnH4yTmiWxmhsKMag6oBZJJAKnY0fJN4GBF-69qh07mbx_kMQ_NScofYgoSgyUNzpICdB9_-aclGgEkRpKjxGLzcpKazRXILhu_fJm6lNxWufkxGHhY1Qu5nim0P0ox22e2RkzT3JKI_QYWNIOnd2Th-vk4Sq0NlnlfQKaHfPNHKEnkp-jc6Xx69RLbETAXFw04eQGCBsv5nw6ivq_4kMMqSkWQkUDb9iLmkGEk7h74Huo-t5T9hEyWJxTloUrRcWsSAEsexWYUhC-addQaLtQ5TGEFegQeJgo1irCZZ7_cJpzyMOV9o1wye9QBuvy4LgRiw66Bymxjw2K98uW0vpq4rNh95QJjR5eSDuHsiAWGTCmJnRLX1j8JAtJXRNP7mzw9FzERRql0CwEA3cefJwd8-ixsduB_3R9nN6p6yvE0Vpti2i0kcq-OTof6BdJgt34SHY2yE2ZZCkbElyaNnnJnUxiKgYYUlBYPAp1zMLC2hCiIRLLQ1FTCMTODqK_5KwafvbtvItOCMH9hwDKMNQr1UWzy9ssxt492A1lo_06DQEhEZmTeyYz9RpkAlUbgFUp3iQSJ4ZNpT3okCLOnCPOAwLaWsIJl5R0nu2WrrHQ0cAmHlGi6shzBNrwmDQdjNrLoEqgoCz1uddL4Y7TErFMbI3BCwpRYbR3JAZq_BnnquLP5nr7Jr3Jm4iERAxYl-hrP3qn8c4SqwaY-gHXGrLyee_WaXmzC0o9iegQAIJh5M7YUCfI8bhKciRQnt69qJdAKaUpunVzC96tMn2IuVG9MEZOPM3ynickR0cHDtaCuBBqyCIFYehraJCoqafiF1SDNfSgwyfEK4VeXmsNTbyyF3j5b0aYoVtJa0CDi7E-xt-0XDoLKVMtM-ywdTl8x1d9ZziIJ0QAPKDh6a5Zau9kmot4w3uBvsmdEwX9MBDRbDQ-7Fg6JFYt0FT0hbvk5UMI--NrJMmtcBQHPKugL9eQup8-y6QFsEIWpiocWIcHEBwrn1s2sFeMpxhWI6F3XarNhSlhzzulLgF4p4DcD5so3kewME6IBT78Df2lhcL-2GIhLF2qHqOb4L9iodQOC4wKrS9NfWpl8fuDwwAuRKTXqqgGQ0n1NG0Z3mRLxk9mgq-ao4yWzwtzPzrgoGfSsEooFqNNgpLmtD-MHVwLtrLyLWeASxnTLqqfPF7UonVdAjDtM1l0NeFmjKmfPsKdHmCd3L9mFNClPXcnZQcXQ8Qcv402lCuJAHmhX3Ic1ksuW8EoDuJCvswvBMJ4pQqYs4BKASFrHEmNebbvHARFXTNzRhB2RpeeEfGmQW7cfAkkfyy4qwPAaK6wwHYLkl5hMJYqLGbXJE3rfuJDHVXHw7jpo43Dt6uMHBw41NDvsJxsy6FhbEyGp1qh4KMv9UjU4clDGXuQ1PGCCURki-AJwUw3gLVohIHzNbBEsTOa1o84WPLEsRR0LBj5z1STZnS50b8AoT0FzbY3poWDeyjbQ5YGmK8DWMEYnzeLP1t0Ozr1DXelpE_klJqAD5nj5ee60MG8mX_DwogxmvfGPJkvcOmTMTpxnz4Z4vVDBBdqi_qPeqdIpI4oLj2V4Bj_k_ancyR16g-EaxhCOnNRcJsrIzLYnwGLgw0bfQZmQzj6VOcTmQw4MkB9oYt-qyAgky5Yu4SwnOVPJmMrbp0Y96q0fcx7WWb-HMFvPEqrJg4kvXGY_trhko3bTDDhckWoH0EjqFiABo03m07_F8wj8zdTXO_Dr1mUwQrgPlBARCKO_S2Qjw7Vc6VxWY_TWMThb7Ial1bSqKEGy_XYoFeQ4Fx1G1FZT6QDi9p9KO6MhBMdJ_zMYhZ_qiwSu0_f7IOgl2Yin_j7BFPPlY_kkjgOXtuagPPC9PAttGXESWJfHSWWyeLQbrrkzY1QOXQeyu4pD2lNFkB61dps3owzX-z74pd2Goz-0deoUVz3UEUyJh_o8s8P5EB122idmVzsCltMbpKAhHaqcdokkom9LaOCS4bCSZuH0lKiQtdmUVDRiUy0dQVaLvjPRy-4KBWe9hQ39V99C3J5wp0xYvw7pLbUO3T5egH_m7iYP3fO5tfcF5CnxPmd7XXzXxkU8it86Jt4PtVq3PBjGFh80vzZassvqT17BPIKVrOy01dpjGhJ-IrWV4nyJrxRIgOYDZ3BWfN6eCmJqnciIWTZZ4eimO8UrVpG6shBAbpbNQLjkgV3SXCFbtVneQBQl1ZLO5jMUgVxvrgLRORumju3eQW-cO5HmzHaEa8cjuTj05laFN1RNGX-tegiBMWKKHNBM1R29kkiUBKSyMMneaWMEltDTznpx_iCFZOpcFTLe9FegdfRH6MZLiMBOXU8RVdhpuYSUloyAuicqQD-kkyKjE2mVFgoh2is2JLjHBpevoI76iRaUInFDrBEXATUcoBYXlpjnJCLaEpco1w5f6yA8TB5rGxowNE5Jt0pI1jAi9b8KB5LpqxCZaYylA9ZfOiYU2HScvHAYd6k_JW0kcDfkrKKR5WRvA7FYuiM-OcsLKexDPmehqLEkib5uFQ-TGsagxA4pRucoGrUnmcRosPvQJlwy66bbDDLY6gsI9nP90JkeT32fGYDHu24JbRN-1G3j2MOpnMMRX90cV_l5wwz0DRniEgNKiCC0-luY02VB71IlliDmMH5ULeOSGOsUoBA5FhpWrrSvSTh0J78OsokYCAJarVBeUXY1PLTEOqDIY1Aok68AzKGefF9lYx0Hr-RnwRD0G-szQVy-qMHIVYfx8lVoYm_ZfCTRewPHR_BHo7FjArG_RTdXG1sW0-fNDW6SoGMmefgKRBpoBKhwEbX0d4_Nd7Q7INoRpxV0JiC95Mw9m9aXUhS2ls0-fCTuk-TQEghqGG7WGLVrZ4T3bPDv2RAyNKPAXTVCtX3N5oqiralPNQLzFxTNw1PihaMSxgziywQkg07kqRDl_uLCg3s38ZsINY9Z4CLNJtpIsXryJaENtRLlpAJggaNwbEQFEUV9p883l00gpIba-NvUary6jvG-o7VW0VCgBg9Fgy0PAROKpluNzwFOG0DDVyJCZBYCb6lRIR6wCw2J4Y-z5doiG8mpwZsNUnE9bPB6SeYe8Up60hRsggATII_K-B1z69FjWD1Swbvu-o18G_zbFigPUmDeDIPJX3HoBNP1xruZEOMYMpVqJDtZbPn5QCewhv4-vHEh7Uw5lAkH4f-yOI-95HKl0MUuyQF6FEuRawO5apgwqD4D9TiNqIAj0w8mVG8mAAmnvnQWBvCQb86OLIdYyHR9QVQHrUl7iX9ey6WTn_hSOZt8__5A_keX_Zv0SqKR3gc9B3npIVBtZZDeU1TL9fhFyVPJJbV2GNguCghFAogWaUYGWgm8x3BHeStrOSG3EpSQJR53bjGBfFAH9Tg5HiLSWR0a1w55oG0mO3Iv9e5F66W-H0d3s7MnEzWt9Gkrn6Ecr8K7iD7bpEhyqTBVdgFjar7miHO6ACB-QTo0YrHra1hDYYkytcQSGxmcjThElCW1fIeEs-WeudzKjjUCNCubRIEgG5I7u4t-X7YIXs71aC74p_yROIcw-_my-9Jd8JPzQRFMKUGu-QsYmvDig6NJWKCIUBm0FJ0GDuQnNN4jnACe4UguZ8OZpon6o5J4y_kVTBIMZI9UQiSmEpNfx6PRAyQ1ILNW5SwrvsDB5NH3D3ayT-9HkikmjxTGgiwca04bY4FT3cc5uDjUEPd-8_k4DgmiYtPTUSP6OCTIDkeBLMml0LsVJqFKax62ABepBa8MIk8ONf5kLGGtloXcZtWxUp7OfB6VmEyrA5SE2ja9UOmbps4L3weL7Rs1kQ2qemjeWoxCLQE465mFkDuvj87TUHfcxqrLZ9Dbt_sikxVS80dKfMj-B5B_PrMLUsD4Hg5heawvlMfSi3yhQhGBCK2ZjH8yySxZ4pgh9806JC6yB40KDEBuwthQh4b3egEtf4sSGf2NEzTjfcDGROs6zUOofwsLGcMKs_XHlAv3KAYHL2pPSph0f9D8fSgiBAu6I6of3kZt34uIb6eQchskbZj37OT_Ukw1MDRxclG5AgYYAw8HZWHNOn6OOQRJs3AvuhT45QI9L6ICetOMOrPUQ8PD0gs1dCNmRI7mYGz5u5EoA6oBADP9krqhLeQXSY3s2EFxokEByTRZd9YtMjObGtHE_QthstK9Vs5IsnlROHO6JnmEqCrX5C95-zGY4zJrud9o5_YOYSZRGzTyLKB5a6leUO4JGx50p4gvVLsn3gSxZ1wWPs4rZpTk4xjzTUk774WSjt6eJkvELtGFCHKB5QBnpSI6neoEG1QKkk-F1MKCf-kZxaKHh7681BMP98ypamq4TO4ilGkasiYJqa88ACrg1HTaL6k8W8BlnhmiXpLDm595M-cc-ozcZ3q3KIhYIHY8XxLlz622lrsUKg71M0NqXRblbIAB4BcOa5bsEl9lFaikMYSN3O4IMhU4bihyNCBti9oGBGxtt0ZO8jSqxAwSli8BAQE9vwqW8qYatBjR303SS7MwwtsFVuxl143pT5vVAcLSqQWuzchYTUJxPVs7G_U1-YEA2b3bVEHAGvs5ZCAV7sPZvDF51E3n044HkDonx4JZRsRHyywDMVEbialwLIBuEUBBhwTIvm-osa9d8wjTiO7VNFBS874Pd6igEzqEceFan4ijgDfzXlXSUlHeALPcFMHsYJa8r8BeL9nBoDTyMR60ssL3wypn0igRKmsw_jciw7I5p_YXHIlsc9Yqk-V32mMayKRKDwwrXNyjx8W1f6poAEBu3DPtG40g06CXza2DLS-jEPGw4hSzU5e3h2p8gZjiH3PDWx-AxXtP7LgFARkcCuAbMMlEhwUPLE_9Hu27JMGTQxn6MBZU0t-QErzjE_lijwqSLZkhnsIK0FRlAifrX4nWvVEHNRYE2gvPaNid2B8uniOIh3HOUrdjGSGMeasHqXCXM46nWXrA8sqTLAZA8jTmG3166A0GR85Iz_TIexnVHGKb8RzI8CI5Tj3yqcdxgOof_bsTfbQMJBJwrYZLcQzfSd07WvNTkkGJewdhERqK5OS-qJZY3OoIfU1lkaZgsFqJbh5FoRy_2gjpBYLNrYiHzMo6eMlbkJeWL-j1IYx-bpLmCF3BrLDq1mSI0vb6lO3q2V3WJ8-EuVNEPlVUOsQMVa5tlLhIw3APKGo-4ZRKdFVbh89plg3ZYm_gwwDzWDyEVXhftGZFKH75Ig_I13IIgdolLAKejDHFmIYgqPjBJIQ3yORgegCyDsLYYagdlSocf-R9si3eMlAcLbILUG8slJQHIW5ZA2PihQ6rzlRSEzGG7hu2L1QOA-L4B5LV4sEVEwpG6KFH8LhdmNvoA0yNUpXM6qrajdu7TgXLIUzoccvbtx8C0k4GMik6qTErTyXlKDvY-arHFx43GTknohgr65XhN_SZjC9l4rbtibFH_uohMRezlA5JcMK7Hrl8r9MouZaekF3EcDtE2CXYhPrY24ekxOX2Md3U9DjiK1jtCyNDrki0U6KQSb5nURuugsgroFqv5gX4kCPw0hAfrJHiJEpAuJIVdux-DO8YlV_VNyIK6DK4lqmVIoWPA5fy8M8DcisFsBcZ8rFMTGGyY05GG_XeyasYO4g2niyNnW4ucvo3LCdskKEA9s7rlGOD-jwJhz_f5xQl82wxodkwN5CaLmGo5KrmBE_7IOAHPhydveXDc_6GrwEi9ckvV6uCUceEnuLQW5SXLFbXqvTc_BliL2iTsqM8OADHfPGvKEoO8Qp5clDlYJM43SnIG7CLwjMAa5GgolGGl7BRBjys9rBv5bRF7VRsSS84OUMGCFYS5MYSgxaYHaGPySqlLXgGK2q400a_WgUPqSJN5VDAzD3TQLknJrS7uf0Pdkgd8q35wWOfrxy38MpYVTiucg7_Q4jD-o6r8xmuhxck2fxclefJa34qr16NlMmvzuwOzeIw0tM1Ihaow3XxdItMuZYLD4xAN-aAQGdFfz4Z4Ld7FswN0kRM4ydDfjEtdwmwxwjikayur0nQp3DpWXG5lQluQHfF0iQ5T9HDN7xtWfNouKg8od6rFTFLrsbuvcxPkEH0sRRXWG9nlATHw23-K-sKjTxTdqbZQm5i1wYJ7ytaD7TULyv9w7QKdD1lMC-BLDOkwHj6Fh75I5d13HT_L8yvEeLTuwktoHsw0IADkSCjHYtisKQlGmyS-dE7DQxjerAruPPUlhgO2jdRuZsP5k45oSQrK-ZEVax1qTYhSksPWZHFWI5MNznCBCFqbEzSQ7sqY_jSWDcecq6zObTPGjr1dgmK1nYkTDaDGSCM4nJ-ksLsRh5NlgnX9kusGHEkcr1S2L4RB0wCShgJkaGo9OxyqSLLVCYlICgzXbQmAVDxnkyZGTneQjDBu9JSaQU9YJfDRYKosFhGnmCqKgfHlKerLPpmOtquU7QrFcMPNxEIN1fWfL4N4SaHIhHBWVi7op7-vo1beRSLhZdwDcWUj2TpSRQtPheJnCAsRGhAnemI6pmKk3MtWudhbzXe9OK3zI7TEBoLNHphDgLSUyiFIpMWJA1DX9MfPtstMKc5BDPimIH5ocsgakAPPTGEpYDOlHH8xO0Um5QTqQQ2ojG4uY149I-w2R4HEKyyKFOHPfLtuu2H7qCwCwZtP9IIerWhNrANdip7dLpgTFN2ryFnvplZq2CywydAJevxgC5tdRuGIHIwDmj201rwhSp5HYCH-_UbitMwF0mpar-8Tu3mEVIZ5FNNYnnSETCKbOhQLTc3MOfU9WpqdkvlRpLbinMet5N2G0D9kl1JxSmLslvZAnF7yO4s64YvwU6NBboEOUYwzxFwivnSP9Cv3Mtmob2K9PpWVwCxLbB_GHCyU2pOmcOq7f5eESN5BxOGSbc6vsMkpB7qStFgY3wpDHBj_DANNgy-ObOYaZxLMwqKIFwU0UepUtnRNWCe8ci4dVIy4JkXGHxF1pXIVk7pZjizwTu8JIK3KzgqkJOypQRjgZvPu3WINvvMSbmI55gq1Du9IxlODGCgF8oqS8TNqSKPxVvbG16dO2Lsqw2beAl8yykx56joz9Ag4nWlswDJxzEG5r7shJ5_yp0v8Yrfd5gBz9Ov6USnc0GOP8mJeLwsx0Yi7XfcMGVZxi1shNJ-TX9cNxlIX4UryZQmM50yBZjOs5ykcligCIHOqA3RtuZ2URClZl3Cy22Imkyl5g5uASuaz9V-Nf_JnpV3RVvCWUklGtMDiYvowsyHZRSPuQddxJAuPkQSbjqAz1lneZyxCQqBDt0fKyQV_Nr59SdkNRNpCur6DLGpsmwNqRESogvnaR7V3ffrLeO-mr2BWYXHGolERqkGTuz336GaVEIUPf6jnCvo4H5GfZQSQYPazpG5XU8BYRYkkdzb1kMhFk1JWFMrrh4hkorfQRlMP6jXdzywh9Em8AYJdCXKzgaa6TEZgDUtFC5Q478kIMxLDLHzFvJ67YrWQ0lXRdLUi1cWgtFcGqtlqumBsr2W0A_uFZnz7kimns4zbsb6hNoqaQfbwd7PIoCV05HZxRqTMue4EJ8Ohb9T7SoIUdZTJlKGD3N7kYo5QhHjvfaNzHQFJg0WUO73GUYS0E5lKlOjnGBZIVdsOAgl88SbgIMz9lgZUECUp2v_PyCJqLMQByI6aU_9-l5zyQ2RtBXiJS3Nadz_72q_IWVPp9QKplowjlLlHufIgzz8-__bUo0tlKwez2K13K76029Jx8MRYof2Uc9QJsS54Br37PhMFEuThhlDRV157TW9xchnT9RA8Bms0pBhmXPwm_g1VE23qkzgsAHswdD_dk-Ta5BqpfONF3VEgw7Ncb70slowtmq7wmANnNkiUagPBPpxfP4dJ2iOMRMqovs73XXcyKEkrwJ58HmVnkEg8X9OVry1mkSIIFkGPDyCYjGB4z_jggwqqKY3TSJe5_SC9YwVSRmMYuIjj-2zEEDJvdISVIInCj5HoxGcMHS4yiLTW058-PQt5VvUjXt57dGDBEThTX2D-hTT7SnLoXxBhBLhEDRkbvrqma-pkhE8rWsZSIG6ghtJOS3EnqL2oPP-pPpzTsghvMtABAHQIIlneLObDn7nCM7LDaLZGpGcg6wVxYt0wjCyOcI5_vigyCMeLTPXkzIsCXJHz2xmQ59HCWGxXeGU2RQWv0V8iDJ0cTwR4YUFKe9wB58iddB-iKb8xjldDYXVQae77OMuQhhaeAbh6jvcOBapyP_k7oleja57uw-IMrFvWBX7QqwwTgvj6JoROCcMwH-IjRVWaxUuFjRFBw-pG7-XfmP1gnkNxu1LuhDE-Pm8C-5ihbSvNqoUoC72docPqKAbP4aJ7hD4ijGkCcJw0ioOvftlwT6k8JUeXiROf3lYlGMHo9vFEku34g5_FkKBcFI_BGqyu6FdFLQ4yqfQmyK5WUC2qDwkJLNE6maJZQ7nefDazmLi1dB8hhh00glE037TbWzXwUvwNJN364s5ayRHGx2dYXr86FgGPulGaZ8pfZw8nKQ6gsdZzfoqFuLBa2VrEH8LT6of_uR8tA7t_rKD2mZ097_vlVaVsaQJ7wUKHOQ9dGqbp2X3CxqTCcuzNQU1Uap7CmV4ohSW05IdEdYghO8goHmzSJSe2W8R0Fc3K8qUheIAmk_WO_yFnJoi9Q4DWWGhgSUQwOZgyNUwqi0Snt3kMkr85N7RUxoLiFDrm8FSMDlnLC6SESKOtRcV3IhoKYozQQ-JfysCECVtZOTTiLq3d3hQESdQFRhRWAKOCN5MC2q8lmu3zh1CT2IeoDo6wscL_3U65hT7KzeIBoIZlFXDkjiQFXj1xwpNioaXYI6bUn7IZgpagQAL9e20K9MJ7T1FUDq3ThBLjDYQjMAGsHII08dGMZuomv4vr6wK23lI8W6zKxuBAI0Y3YsIE2QZwIpR8dNjcD0d8hBP0T2MDdEKsbpE39YDR0q0z55FxvOFsPnIGV6xY_bozGgTd2e9_PCdyLlkpXkdUVw73G-Fs6xnL7F_cRNqWAynjRQRhoBC3dTiu9leWiX0HFwfuoOghNtXFvQCP0-VcC9CoHOSHhkhzXXEVUNpMyo2SxcswhvNVmmqitBKteAPZ-UlOBxbRsHCq6zOqUUVayyEsSDKNE04sqlZQwFS5vhpr0MVaRaDkMvbIDiX0ksprRjCa0p2XrpzdWEyVB7q3835Zxg963_9J6ZLeyWoHkkiAMhJw7Ii5CZ1IVkCbYZphE4gOJFz2KtwfzdMwhmBTBqk4K3hOpE5HoRmgZOBZSs2FRsp5JpYxsasMfiTAlZmqO6rwReRl_TvWSJ6sEKfI-rgbO4URP5VDoHhVfQH4VUaNaJTNDOrqQF7J_4yw8szvyWw1Mc-Qh2B83xWG5EBkkWigPhQO5n7X1wgMRBRBLTu-w_dYJwo_5zISz85qA1oC4h87oaYmF9nx5JMEKeO0Jvv1iLDs7AGqBljefKB-jgSr3doBBC6HxAZLeHjDIytiGuycnjJL9xfGcHE4qhyplcAQJGewInaVQQAKjnAm8vHdzfH5xFPkXQqhNjKWKIhp6wh3C94hsgIJlGTqwMI_HgduPybhRQ91nRKnK8qn9sQDnR0YD2vCSsJa25XS9IGqTu69GSM8l56qvQE1Xx2TC0iLOfT10uP-A3VGtrOpOkAQcxrdbgsDnJfMmeEiWv7tRiMqN7bL-pDZz4jINghqVRUE4MWGDqAcT9a9T--MbIyOQc4f8C73CJq3K1AKWzRLrAa5hWRisnW3QLwu0ioovnu0AeVNWmh85B9fRTz5dtq5ZhF1ZWnv0Sasm3OTgWAksoBPHpnGXwAmG-8UyhOD5qa7r-5NBYXZQwrYOQexDYqpzyhpXxPiRP9YJnv3ArBPIDC2JfnSEdJFK-1mxgOBxaKvvQKFicZJdbKWr2xafFS_wX1KoKGaX3m6gsSDG4nesNQ2n8NbOmmkrOWEWIzUSx9NGDfuIq9OtglegFEMX6FpcDBsMeHGyAQ_vtjqhyOp2quuTRPDoiDmv1hOk7oAMQpCUQ2dvhsimwoi9yS_S_WnCbYwOFjuYh4on2B1GQIMep9QqbhVmcKPKGsjRppjwi-GQ9yKpZD9QJSv656hq9HlIdYMvJjApTtyRqDvFSPAbHEf61rVUPcaQkvVNmtsh88mDiYjtfpePBE3tWHhoPO6XTeceUqhwN3FwBv--ElhZL28JeZShGu4iraD4mNAgBJYgZyzy9wIumkkEuWCYSbzWQVTLy7ZQdtly31mIbOo2u4hnOz-AkU4ibAkYkyqUO12YwDn0ZY7F8UUhpC5go5Qdii6ap6nQoeQddLiUahA6RyL5znhuCMHe1TMrYWUgUCIsHpz1fkfYBy5LWPLFmCHna7kO2H32o4n8fEbPP26TSi7jgUAP4JXF1wHmafJKzOUZDBWNFNQCErU7wtqlIez2oc4k2BbV5XoD4uigi2RrAkr6JTvDZjPh06XD3o1NMQQd74GzawjYjZSOtLnqiLNnTJCuaQQzzUowZuimePLb5lEoVX6IWSeOjIvyfUKaFH6hrG2BVCvvZ5Y69d8IAu64CQuugi90pFnqQ2MQAU5BeXUP4ipUET4hhQjttzWtTgZLgmunYV9wE6kCLcpgflLeJ2JF1LIzgXlSena1r-Lo6El_I-B4YshfpzBduBB0-8i6VEgngDTmPAYVB9W4mD8-jW1EzuLqqcgRqk9CD7v-v8elgBciszFHB1hYdvEkdpIdm2fmJMbsmxabd2EYW-K4CCDRGlXsWA8NEQ5i5SkGkvmBwBDw3ykgYtaADoYJq3dOhIRY6D-YNAfQRMEOGqAoH8KCR08IGNlqsMVgjGTSGsblK3xXZLDgaM5h8NSgRmKb9L212egwWUWF-hmDVPhFmjlZBknQBA3Zsx1siRraZA6Z0hq1-hV7RE-UBaiJMDYgRWJPJozOHdDABafV7DrmLJtZFvnH9ezCDWxMgh_h00QJ3bL0sG67_yrely_a__eUtadneVzCf-4AB524guaIdsrIg0q5n8E1WQrpE6tjwMfX7dgTlnGOqbtOED5UekKlgsU8KVPQ6mqagsXRSoRM_UXadydJM08SbgCkwvi1sBKNdKiHGrVpmGZdBZuaiskFekE0wI-NSyy5ubHo6Qwg7gJU06BBYXtIQwk-3FrTNA5wi90Pv33TGrKenoewvLQya66XXz6kw9RteydhWOv_xbsxxNyhKOG5UYq-qaGeNIDwcLokC5AOyIS-IMGlxh5jTw8wwpgzo9ahogcGz7Fgu0NelgWpsrBQVkWfhHI6jb2suOrSpL_tQppJ5NjoPFlDimwmrYllFTUHBbq3pXRK4-ThAL2V4owwBhCykNwr3Hch0FIsYfZ2W5w5cmrCqIxn27oEj8JWTNszvp7BAa1xW7zrOMV6FJSo4Pg7FMLhJiMimDQ-aqUVJp2pwaX88WyMDmCnsmjL22bW5hV_wheFTtNKAmOdobl3TJVaiVPjNo8axEhWhSKofHwMYakvFErW1mVS04UmYiGBEYscRPzAo2LnjrTDNLIOcwdmABcxLkpIwrAu3iEgFBRIjpowC-Jo_07rtedQJAIkvy5zSvOhU01WWbpGzalAK6I1JYYs8uuHlvIQ8ne8ik3NaPpEPA5_2CIcjDXiExs6euYjaLEM8JB-yI1KUtbxxFO34mMaj7E7rN8lN_13J-nwTuDd1yP6wGyI2Xgh4FYQpamzIaVFM6nCbOLrOBEUmmRnomEdkC1e0jIlzqn18FA_hVpWJ0HjZxizYZBgYntNQGRLMyjStUrHjTTVOzWdS4iNwwO8oicaHErbHcoSNM-esI2BrashGenWZ7fc2IOJX0SgeqrmGubg5ut0Pl9pKmMP8GkuwFkNQgrtVzMruwXJ7-amH6bKwhWxFMv07rO4CdajZTtKty5aALCq5L0yp4ozkOrRtE40Cnf18STkH7gdObIjvjB09VgxDge8CjJQ6oGc6xAkEiQr0wC0zn9pqvxF2RtYQI7O6PNjWXjFMu7bXAAAOwnlqlEHvFOhT3CPdaSK8b0W2opKtHbV2qhkyBo0kPFE4CyvfSeAzPuoLKEgShmgBdtcn0k1RFvbB0iAU32PfDK5nxjvX_ISXCedB3ub-SvAOfyCJTQM9SbaXgEtbVpA7f48stUS-qBExSn3FSEL01GTgOp9iv1QVPwHeaTCUiGkeR4GZGoBgJQ9WaGRvklaefNkFCQKAUpoAwudnKUDht8yoTdFyT3amsH6XhdE2yr9PE-rkLKAD5ViiJQelxQZZ42AqDDvWTdzCOAzoY95W5ncmsQOAQUHP98Zd5zlqcPDpVn2k2swNQ-tQIA1sfmwilblP9kaxT6J6rqRDz9yyNPvezllWyG4nJ_McbujmndSVrFC7JYrusDSzNEAZIiDdIr-o9sMr9EGlZgQlWN0nVIcZQM5Veb04YpfTY-VoxqxBl10gEqjCD-3hQIStE5Q9c5M9ONCmwCOOAHoQSuclO68tXq4YFEG4Zs6pIDIwqDJuYIAh9-QpKBE0Ty6UN_wET_33b9SUMCtE22EASamBBMXlmjXMYlMG_Jsc4ZlpK1hQ0uFRNOZWs2WEikiWqnkfRqECaI-IG_jDluTYAwjtl6KKAsb1FV5koDI4EWCIYCHKzx433J85L27PpCDhUZVhs5YV1JfaVjs53YTtYHCL-2ZeGbRpTIMSEEFZx0wD5MDYFnbU-E7Af1Y76MMKVpNVvdJJrVPdmPIhmppow638KAM3bFSEBLJL1rVYwLkDk9vjEaARiRvm8DeAYBQg_xKQkcS2YzAGHVpP72chKwi47DQilByCn6O84yAqVJ8RfdsQ21NqVH_XD8D712vI-bCt5MirEIHVJxNZDM3Vcl0hBTdzNMsbgBwW821PVTDeObFhJbIP_8NkndpCogwtSGEbbbTkwUpiKKbFpBpPomHDLpvgipYjKlQVwle3LJbhtmBanSHKbhME6fp4hifXG4TGuJlVeu-iYWdT_040yF00IXq5XS7GGLMILuChMNfbi0YZwvkKOKHGGxkyma0XiGngz47FANo-_qi4yDhjJPvE--9onn8GvYW59IxCjSaX7kCmqQDM15DEq5ORlCgQQotRxnzHk1fuD-VX0D9JDSgpOlonmtBHWcuKniBOG5bRuDWKfDO1RX_CPL53GFat7q2uhbrgMWMx0oZcGzzeX9CWh3mRcQC40EnxM0nqQFis2ww002oQ64ZMNKkewvwucBlDeLTiKBMRtZgE-il1mtO5u8M1BIQXtP4CcdvDPcevqETEIGd3hTEzffEAGSrT_O1bEqtDgiMGhlqaPfi1VC0raESw2fpIYVgb9ivcfFI3DS2hiCqbw3jAeaF1RaFoZnnc7NtSnSYX-ENsfJ0BegExsN9zbh4SKlQCzXEN0IAYqVOEiBZNA_d-mMIajUQ4Tx9ctStHkDbzQD27D8ciAiC_LGqT-3wEuMBgExUQ4jyaSkueNWyIgJYCKMjijxqEGKCrEIubMPaiXMAVYFiEjaTpafaWqioXsIqRSd4tm80HYbI7DWjINORQBDGFJS1m2HDwAtBznERRuqmkBQlkqj_-qOMkG74_gqNHfHeFZpd4Mq5UQyeT2jSAdIUzvczqg5DslRfpjTQ2Imd5atDI8BhW-6nGGd38SB4b6w0i8glwWZ4r2Rllx1uo_si-VVWkhVqqVlQltnH8MITxrXRXzpSPpg-QosKsWAGJSUYP1qNcyYwIIQRsMfyGVuLgNyRe7AXVahAp6Hnm-kj3gZkTTOa4DZBmlsZ93LnQIR8KAjYMz-KFHsXIhFkb-LkbGqun60qMT5a4ao-n9RoYEFODKtsHRBdCtoJjgGz8xGzJuwBZzUd9EvvS3KfusW-KqjtAOuq2q-_jUdw3p4jPiV1JfVGdZA1iJf8bOtYbS5ZoR6wTv-M6rf99hLS-XDX5FAElHaRTG5PjDNYgqJNEZ1AnEzI4_OcOKdl0Plro0DhoBIYEAUs4oJGJVkYVrH0FVw2MDUiud3CsFLM2Wq6-uEQh6tC-2hWT9WtcdQkcnZZ8AmQJrQ3pHzY2lS-ip0pLZDzUmbPPpHNtRkL-sJBhJJ2ULXiUodHL0ZZZSi_ZjMrJ-GJQOfxkjOZWB0tAr_z09ogzhGMyt7IjCnwuf6GNkZS7wNcEjvaSIejud9y0bQO8an0QCYruKt2qxM0pHAoKBwqId3Jxc8mEvU3LxtrHMiebzRacj1HE0IHkoa2pezSJUhKN7EPl67CRZvrFcKw7deRCMQeFjnMMPmgN7z3ISnv2AmDuorPnCecitZaptGJchEn3T6nB-U6g5ccsfyRarK4vs_sBSBRtSRnX1wJM0mG5KQvFktyXae2Po5-hoeGLg_ytdcV83oxSMgWN_1pAwxFmyQTurh8XbXRzLUCXyFTkLC4IZ3d9VAAs_2IW04EfvosovaB7zT2JLC2zXmcXADCLrXuYTi9IOcOAajGR_oHzrlxghLnR4dK3tzEgNfpiPsE5xsHwhfkjxdqsyuQRU0yd_StoV9gGJuwHHAJqpWO3tmBTx5yrKJSh8Uk2GsD1EyC7YHYMMkJJrUDXeIR_m9_6Q0jKIschjl_GExSWtNyztWp3brbsb1-udfIoQZgG8idYy4vOi3aQ-rtbuRX5T_uL_hVFqP6TNPGC0Jn4fFgQyEcQ2gwmCMvzGogfdlB8ZIMhhUh29O6tB2CFoUlcgn-QWeUGNgu6NX8zA2rzMRX_-QI6-bePRieFLT_dxaT4eL2r3JyjN9nw3LMyQvncOWs4JQuynIZCpCR1NofBP09wEQQQJh-y2xSoQGwiMrS-g4r2-WVEndlFPfJDgN_nMtKoAYHI69lQEZOwsoJFukKQh_E4_AxbupR17_w5MiGWVm8JZCGg_nqNlzx2APqyaWl_Rlj5kbGxWiPtW2XmGegnNI9TVC8hb-i8jtBYMDBOWsWNPzxEkNx1sAKnQxwyS6-wKy2KG2YxBuLuUGwVzZ65vY8W1eYwJZSzHL7tm4pT9AzRptwsOu3YKLFFIPSNysn1SJA0R92TevWaUPMCjDShwuLhQ0Od8PA_SYFOJMJ4xoQpbR_5HjPgC2VCcUEOqtcUfIFRN034NIZUcNTNKIPrD7Qq2spay23ErltiyP4trfKZlqtufTCf2Qq8i2KWmJTdsdVDov02NdEwkR_GqwqV80xxJ0cL_kKlB0V0ZeE6gQZV_lJ2q5MOi8IJ_gpBaGSx45ITI8ZKiX7y69CACkA6MFYUB_cwkDGFwb11iImaFxfQ3RDhzzHSE5iHndINH-4lR3TRc5FHdTzqRU5-Iw4zCQ3Zt47pZwsRkBsNxbB3gYWc6kVke1JBTflzIPAg2jOSYivvrEJ1NHoMotKllMf_jwakG4bj0yx3ZzTiyBFyCouIId2zAwVNGsL8h7-evMxn-CoF2eiwZIp9qhq7gReTjGTp4VdPKcDAaCv3aAdxrER53P1pmI2fJiLssgvzQSDvvX4tWpdKCJAmAhgpnDXFFjPjLNiJCvAbFlBxI_AOYzl1PAFHX-0TKk0zQ9kxb9Y8KKERRkEtKpP7oaUXcuAVkgy207OY1i8tfdgLG40BhthQEE6THS8gPopKhqqFQdXn-Jh-6XxQQcdMK_ETbaRtflWt31ChKSVqT40BLmivsBrmRb7Q_CfpxemjXkuaNtg3yy0W5FFS3YEuwwcIZRYDT6Wp235xVgKz1aDgPDmL-ubLpWWXqI6kwscYfbdSW0BMfYItsbEXToGqqdLvEP4J2HHG7WRmOa_T2JaHZdTGUk-yIwY4_Dh0LI6LExW12j9vJ3Xnq8wIQ7WUgJJI--PbmXl9nPVS5yOA-tJSnxZgxbZEr8xK561A4n_dyKQPZlQq1iHUqllwCHTFlE1Idx_t_G5BC4fk4gDNA1EVGjUBqcZiTipiO02NWuMWJVUQJOB9gdYii-pX4JfVj95zrEgNsqm7tNXITmOM-2PCuNDfZQIbrT3tGawMOMqfhCNMSmM48iam5TmJw9Rha9fR3k4aD81EBW7ME8F2qt23CY5SN4ZRE9vmsqbOVQS18fxJW124W_4zxEL2hAQiMwsxxCa_u2bQ8qalc3guMCMsWF5DSpAkR16920Gx9xS7lVb8L18uKYFIll3K8DE5Ae7fbTCwHlj5paDyiwaH49VosLuRknNhemugEXABomFQMTJjaM3rFTQIkMnNtDpu4y1Y2IugiA3JhcI7ES3yHx9axp7Pzkf3F8WKMWjLseStHDvhbqxC5q2yzMlH93n7HRwpmHrxEDjsblqzmFwwaAkwOZ0BFgEU9YsQuX4iTCrr2aWs72qMqA7KzpaRRLwIQxsHou-Y1iESoqa0MxMDEGDbRYX9rMVOAl1QPNxP4viyS27WE1mrARkxaf6FL4ELBM0vI01J9zohN3_sWafOlPDyINK_Ar7omVFr4ZSUtIan3jInrPrWdzXqmY3Ua4Cg0PqBWrIpsn5D9c6catza7szKj5ZkrXgXimX8BIQ8OZSJ-A-RxRrINt7bILzFEV0yNxpQp_TE5xpd_O11DqzqpVF7pjJED-Wg3wVu0mq_XbuttOGhCD7Yiv41IutnshUF-Gx_b8XgpIhgW1oFVecD7UEUd_rBNryNFwX0K38XBRKYGVH5dDwf3JLo0mKnbPWKASQTnibRUNfdJ1isvVaBAylICSgOn9LhVO_dsMK8MyS5yNV18YC_CqWyxIGZ-YjC2JsdjbWdrzmeQ-wsOob1SeBYP-qmMGAQZyDBnsi_sW3mxWTG1igoiYxyDqnmaFUKox7T_l9msWS9MzBdbe2GlnAQ5AtyJAUVSfa3BaA_cFT8ioESQIezJpyaaX9TSWe8iN0Fjsh4nW_3GMdYjGPJp88N0ANXBjzNs57Ib8TOpGeOkVZhkBljiKGWgy2PuwTq87KeNJ0DkgjzyZHAU89JV49y8BULChc4jMR4C4RkfvBcsw-lSL37i6YxMAX-sQ6ETz06bGFeoUr--wj6mUmPS6JPTZeIBed9NeeMp6m-BSzuXq8hY2KkNBkkblHiKonWbo5auOGG4ORowDpzt8B8GE8CpDHx64N3nVxCgcxmWrxCl5H_Qc4kN04OaC8-WB_HVUs0L0R-hA2OQRtEkbexxf4F_ObtApFgwJGM7yKA2lK7mKg9RQmcoUmp7NQJQ-Lz1AU767BohJPVVEgLsPsezrTTjHNyvem0YbbR31qwPZCSUP8czOXrbEHc5ngbrvhHK5RaqOmuC5SZcXQOW6KaYCIFdGil1DMZl8G1DsAHOjexWeRL2IXPdko1YUx_LEfUjPNZJzpMfLIVzXslN4KI47bANW_GCmUFqvQ_nZ8QLgp5T0e46ubDm8jMBcjVlEE2xDZYogKu1SkA4r-CyAY3RxbNzqa51nmU2-SMHRDzZBSBPKOgcWLYAxKMGzA_9BcSrYSWeVXgAD_lflCoX_8FMy4Onm2EEOyjE3Gk9CsBPHM8gW2Jx7SOf4C8tN4IQXokY8y-mkokdYB9s40p0KDwZkIn0mYLtSmrN6ks1AJrzWgrE2E6_RocyStKji3FzbNSiMIHnBRCTFFECW6W-oy9hIrc9hutaclICRKmt6E6wKRxcSUk-jRgTFBti8DaJfRRnWLyTDykFhWUh30EhV6VnnClxh7n1oIoxwSbwHkSaHbv_qRoTbVrf-FC9BSxhq9ai5PgSKklYVO5hb8ueihZgCIHqhY8TDYwPm8QnBPnli0_6Lb1jxXJ9YqLjN099QEGrgzmk__t6ii_aCa33_gkUcWBy2YegIpmMqxw-xwypoanD689fJUDpbcAaNIe-JSrvItKU1tYop6IsIS19ohm9dPEhSKfIXN5E2NHeb40gq14tFYojAiKAljgMSKLngCfZlB1rJQqMmUhRbXXqZ2rHLKTFAn3avmz5dWI_Je2F1qN-bGqKIhINUnj0trjQGKqAnNvoDWQRZtkd4acGTtw9HVqQVhJGkF_kZ7NRecH0qvJKL5LJO_dPYRrCUTrawQevZArAnrdO8hasQhwMOn4MxCFilm4maTuT6QOswrhi7UBTuS_VAPqtEKG1TDx_JWUzDvfrR3yvHDAD4-sXjNbQ1QaBsaA49E3EcWbARWQL9KL8BUlmikDqJP-b_tvJXyTsjVIIzy4Zo2BjBzy4l9io5gFYx3911PlWwOEjhcIQOusHsK9FHL2zVbE0kgjc-2mobrvCrDJAV_ultcE1CbI6AyxHn1G3osuXhECH0M5_MR-SA8gAEEUxJMDuMyRRGf5rpOcWN69cruMCBJM0tsSd6PzHTtYDLS-fAFJcXtmTo1Aov3uw3kAfjae7G_tWgVlWEqr5rAA9r7IV4YV6EXMj3Qq2hOCJXxP5k8IfODIKLLhD3IjBZKbhvFFRWF6NHhSrDP4DWSG-Aw9hvVckNQHbqWf8yj4_h8ke5Kty1YHhEURJyqwgenAQF-wv4D1FNRiQ6UHQtCGQBYJFkdP8DcG8LZkHSU_bxdp2B3KCn1GpZbk5rBFKGliulqva8USJlt_dLOARHWLwlWZ-eY-9Rnno-trZBTxVGXKAYi1XBG8rU8Tbvk_EDphOanbrpOicNiy894miEHCp0s4Z-kYHrQAf2nGFhngYhv-i4JD49RMd0KN2iwUJI3fsOywztxGJqMq_9SoiYNkMwREzC87ckpW9AW0RQXs6L7DXtZaFPqxMpDemozhxplbDcBgJZwLqCPZ1vHwmrlRZ8Enu8XsRDIKXYIPO6M_fJF205wTZIXEyeC30N1pMfJUVkepaIrYWuCaIPenbT1YsKW_KlZMIzGFMrnTgDHBwAwikgg3JrTbJadF1IjzyNe3o0ElNcIGHtXnSm5gfq2y393iXRO9B1xIEf9kn3MRuPEwELq2HAI5eRWjx6MmB4X8xEg3PzXEr0jmGjGpSfG4O92YBIEUEepgs4fDBkmMl7EgoOFMKpBjKo0CFj5Hoyjk0nNT7mvBqGEJYgquTSaWQa-hzyYsMardyU3X_eEs8rcQxxEOK1LFuL3FLt3AbI_1nrkNE3qZog_jx6Bgnl7B_5Hi9dGpipKgr_UsM5v8WRahmsNH_CY2bMgGb5JhHBz4uEKJWxwA0PvRS71lbpwpoTHNPDdpGCmtx8vouRF8eWJRM8dicQS4xYI2UV8EYBKDtJ4GRrXAnkZsYsqKX34j9M0_UoIBZhVb20lwC2Sl6CrEot2oCFdUhCI4xmDejzdUFoX9TeKcDN4neoRAg5Nm5RKJ7icAey8eTqkPZHx6BphGIQzT_4ttlB8ZYmQymbZLSGZ5zQ1vbb_Q",
                TkmTK.unitTK(10),
                TkmTK.unitTK(2),
                "chiave qTesla + green + red");
```

resulting in json 

```json
{
  "v" : "1.0",
  "a" : {
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10000000000,
    "r" : 2000000000,
    "tm" : "chiave qTesla + green + red"
  },
  "t" : "rp"
}
```

##### Get a wallet

```java
InstanceWalletKeystoreInterface iwk = new InstanceWalletKeyStoreBCED25519(SimpleRequestModels.EXAMPLE_WALLET_ED25519_QR_EXPORT, SimpleRequestModels.SUPER_SAFE_PASSWORD);
```

##### Use the helper method to sign

```java
SimpleRequestHelper.signMessage(simplePayRequest_v0, iwk, 0);
```

Last integer is the index of the key, default Zero.

The call will modifiy the `BaseBean` updating the field required for the sign.
If the request has a **fr** field make sure you use the wallet with that public 
key otherwise the sender will be overwritten with the public key you sign with.

```json
{
  "v" : "1.0",
  "a" : {
    "fr" : {
      "t" : "f",
      "ma" : "v8a3bHFvpKadNvBEYGhstAW3hFQ9YTonsClrSML_T_4."
    },
    "to" : {
      "t" : "c",
      "ma" : "Iq1wmZeyhgjdeoaNAnBFHtgfXzyw_JtBDXc3ij1ybWuT6G_vWfS6U3YkuBJNYs3r"
    },
    "g" : 10000000000,
    "r" : 2000000000,
    "tm" : "chiave qTesla + green + red"
  },
  "t" : "rp",
  "ts" : "Ed25519BC",
  "sg" : "XdtrpA_fI1-rDgvvxwDB7jOg5PZV1-oTrBGRNLyatXjiPBlfkicVLsh5uxFsLCRahlvdhzyda7F40VwP_AX7Bg.."
}
```

##### Step by step signing

 1. set type of signature in base bean field ***[ts](#Type-Of-Signature)***
 2. set from address ***[fr](#From)*** using the public key of the signer, *the public key will be part of the signed message*
 3. create the minimized json of the action value ***[a](#Action)*** `{"fr":{"t":"f","ma":"v8a3ben ... [OMISSIS] ... + red"}`
 4. generate the correct signature e.g. using `TkmCypherProviderBCED25519.sign(iwk.getKeyPairAtIndex(index), requestJsonCompact)` for ED sign
 5. save the signature returned as ***[sg](#Signature)*** value e.g. `XdtrpA_fI1-rDgvvxwDB7jOg5PZV1-oTrBGRNLyatXjiPBlfkicVLsh5uxFsLCRahlvdhzyda7F40VwP_AX7Bg..`

The relevant java code is here:


```java
package io.takamaka.messages.utils;

...

public class SimpleRequestHelper {

...

    public static final void signMessage(BaseBean baseBean, InstanceWalletKeystoreInterface iwk, int index) throws JsonProcessingException, WalletException, MessageException {
        //set type of signature
        baseBean.setTypeOfSignature(iwk.getWalletCypher().name());
        //set message action from field
        baseBean.getMessageAction().setFrom(getAddress(iwk.getPublicKeyAtIndexURL64(index)));
        //create message to be signed
        String requestJsonCompact = SimpleRequestHelper.getRequestJsonCompact(baseBean.getMessageAction());

        ...

        //sign using ed25519
        sign = TkmCypherProviderBCED25519.sign(iwk.getKeyPairAtIndex(index), requestJsonCompact);

        ...

        //update signature in the base bean
        baseBean.setSignature(sign.getSignature());
    
        ...
    }

...

}
```


### Verify Signature

#### Ed25519

This is the easiest verification because all the data for the check is in the json of the message.

The relevant code:

```java
package io.takamaka.messages.utils;

...

public class SimpleRequestHelper {

...

    public static final boolean verifyMessageSignature(BaseBean baseBean, String fullPublicKey) throws JsonProcessingException, HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException, AddressNotRecognizedException, AddressTooLongException, MessageException {
        
        ...

        String requestJsonCompact = getRequestJsonCompact(baseBean.getMessageAction());
        System.out.println("message to be verified: " + requestJsonCompact);
        TkmCypherBean verifyEd = TkmCypherProviderBCED25519.verify(
                    baseBean.getMessageAction().getFrom().getAddress(),
                    baseBean.getSignature(),
                    requestJsonCompact
            );

        if (verifyEd.isValid()) {
            return true;
        }

        ...
    }

...

}
```

#### qTesla

For qTesla keys I have to:
 1. retrieve the compact address from the ***to*** of the transaction.
 2. run a request to a server that returns the original version to me
 3. ***calculate the sha3-384 of the address obtained from the server and verify that it is the same as the one in the to***
 4. apply the verify by passing the full address in addition to the BaseBean

The complete procedure is available in the example classes and uses takamaka.io 
servers to retrieve the full qTesla keys (only those of nodes registered as Main 
in the production blockchain are available on the servers). 

## Encryption

key **ea**

