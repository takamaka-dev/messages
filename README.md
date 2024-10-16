# Takamaka.io Messages

The examples are provided with prettified json, in the real application the 
jsons have to be minimized.

## External json envelope

All jsons with the same major version (e.g., 1.X) maintain backward 
compatibility; a parser created to read version 1.0 will be able to decode 
version 1.1 simply by ignoring fields not defined in the version. 
In the event that a field is introduced that breaks compatibility a new major 
version must be created. The json version corresponds to the field with the 
highest version number.

- **version**, key:"v", string like "1.0"
- **[action](#Action)**, key: "a", the data needed to perform the action
    - [fr](#From) from
        - [t](#Address-Type) type
        - [ma](#Address-String) string
    - [to](#To) to
    - [dt](#Date) date
    - [g](#Green) TKG value in nanoTK
    - [r](#Red) TKR value in nanoTK
    - [tm](#Text-Message) UTF8 Text Message
    - [ew](#Encoded-Wallet) Wallet encrypted with password
- **[type](#Type)**, key: "t", what action I expect to be performed by the qr
    - [st](#Stake-To-Node) stake to node (v1.0)
    - [b](#Blob) blob (v1.0)
    - [rp](#Request-Pay) request pay (v1.0)
    - [st](#Stake-To-Node) stake to node (v1.0)
    - [su](#Steke-Undo) stake undo (v1.0)
    - [we](#Wallet-Encrypted) wallet encrypted (v1.0)

## Action

### Fields


#### Address

If it is an ed25519 the base64 URL encoding, if it is a qTesla the sha3-384 
encoded base64 URL. If it is an unrecognized object (e.g., an incorrect "to" 
field in which a string has been entered that does not fall into the previous 
cases) the sha3-384 encoded base64 URL of the same. If the address is in 
bas64url format and is 64 characters long it is considered as a compact address 
and used directly.

##### Address Type

 - **f** full address
 - **c** compact sha3-384 encoded base64 URL


##### Address String

#### From

Source Address

#### To

Destination address

## Type

### Fields

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

