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

#### From

Source Address


## Type

### Fields

#### Stake To Node



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