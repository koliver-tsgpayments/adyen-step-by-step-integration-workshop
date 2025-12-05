## Module - Preauthorisation (adjusting payments)

Note: This is a continuation of the advanced-flow explained in the [README](README.md).


### Briefing

Use manual-capture to keep the authorisation open, then adjust, capture, cancel, or refund it. The frontend now surfaces the `pspReference` in the console after a successful payment and exposes `window.fetchRecentWebhooks()` to quickly view webhook activity.



### Steps

Read: https://docs.adyen.com/online-payments/classic-integrations/modify-payments/adjust-authorisation/

You can start by implementing the asynchronous flow.
Once that's working, you can modify the solution slightly to try out to synchronous flow which would require you to keep track of a blob.

For this particular exercise, you can just manually remember `pspReference` and enter it in the subsequent API call.
Alternatively, you need to hook-up everything to a button. It's up to you how you want to do this.

0. [Configure your Merchant Account](https://docs.adyen.com/online-payments/capture/#enable-manual-capture) with a capture delay or manual capture
   - Alternatively, you can specify a [manual-capture per payment request](https://docs.adyen.com/online-payments/capture/?tab=individual_payment_1_2)
1. Implement a new endpoint `/api/preauthorisation` to  [preauthorize a payment](https://docs.adyen.com/online-payments/adjust-authorisation/adjust-with-preauth/#pre-authorize)
   - Handle the `AUTHORISATION` webhook
2. Implement a new endpoint `/api/modify-amount` to [adjust an authorisation](https://docs.adyen.com/online-payments/adjust-authorisation/adjust-with-preauth/#adjust-auth)
   - Handle the `AUTHORISATION_ADJUSTMENT` webhook
   - Note: You can implement the [asynchronous flow](https://docs.adyen.com/online-payments/adjust-authorisation/adjust-with-preauth/?tab=asynchronous_authorization_adjustment_0_1) first, before (later on) implementing and understanding the [synchronous flow](https://docs.adyen.com/online-payments/adjust-authorisation/adjust-with-preauth/?tab=synchronous_authorization_adjustment_1_2)
3. Implement a new endpoint `/api/capture` to [capture the payment](https://docs.adyen.com/online-payments/capture/)
   - Handle the `CAPTURE` webhook
   - Handle the [`CAPTURE_FAILED` webhook](https://docs.adyen.com/online-payments/capture/failure-reasons/)
4. Implement a new endpoint [`/api/cancel`](https://docs.adyen.com/online-payments/cancel/)
   - Handle the `TECHNICAL_CANCEL` webhook
   - Handle the `CANCELLATION` webhook
5. Implement a new endpoint [`/api/refund`](https://docs.adyen.com/online-payments/refund/)
   - Handle the `REFUND`, `REFUND_FAILED` `REFUNDED_REVERSED` webhooks

I've verified and tested the following flows and understand the successful/unsuccessful scenarios:
* [ ] `/api/preauthorisation` -> `/api/capture`
* [ ] `/api/preauthorisation` -> `/api/modify-amount` -> `/api/capture`
* [ ] `/api/preauthorisation` -> `/api/modify-amount` -> `/api/capture` -> `/api/cancel`
* [ ] `/api/preauthorisation` -> `/api/modify-amount` -> `/api/capture` -> `/api/refund`
* [ ] `/api/preauthorisation` -> `/api/modify-amount` -> `/api/capture` -> `/api/refund` -> `/api/cancel`

* [ ] `/api/preauthorisation` -> `/api/cancel`
* [ ] `/api/preauthorisation` -> `/api/cancel` -> `/api/capture`

* [ ] `/api/preauthorisation` -> `/api/refund`
* [ ] `/api/preauthorisation` -> `/api/refund` -> `/api/capture`

I've triggered & handled the following webhooks:
* [ ] Handle the `TECHNICAL_CANCEL` webhook
* [ ] Handle the `REFUND_FAILED` webhook
* [ ] Handle the `REFUNDED_REVERSED` webhook

### API endpoints & curl helpers

Notes:
- Let Drop-in run `/api/preauthorisation` so card data stays client-side; the curl below is only if you already have an encrypted payload from Drop-in.
- Watch the browser console for `pspReference` after payment completion, then use it in the modification curls.
- Recent webhook payloads are available via `window.fetchRecentWebhooks()` in the browser console or `curl http://localhost:8080/api/webhooks/recent`.
- When using a stored card token, include `recurringProcessingModel`, `shopperReference`, and a `cvc` (e.g. 737) unless your token is already set up for CVC-less flows.

**Preauthorise with a stored token (include full browserInfo to avoid 15_002 language errors)**
```bash
curl -X POST http://localhost:8080/api/preauthorisation \
  -H "Content-Type: application/json" \
  -d '{
    "reference": "preauth-demo-001",
    "amount": { "currency": "EUR", "value": 4999 },
    "shopperReference": "KevinOliver",
    "recurringProcessingModel": "Subscription",
    "paymentMethod": {
      "type": "scheme",
      "storedPaymentMethodId": "M2X8CCCFP29F2S75",
      "encryptedSecurityCode": "eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwidmVyc2lvbiI6IjEifQ.VHCKCwP5F1nrkN1zoklKza1n842O9gx0UWhD9GZUD5LVwSW93YcDv7QmF57AjYpZI8cnKP0dRox2xEQGBrCs-Xr_kPct7NWDqhIDQcebJonlTaDNoKi3gbEcNVM_OEJ5TRSm-pff0ct4a1l6njSj0HuRAZK-fT7Lp2KAABhaCWKKZwORv5hhoVaMBh-ACWZ54n66DDa2tADA4dH4HjhHuT5ilIeikpOBDW2ceUjsW6hAIWF7hgQSTsJ7rxAmQutUnp2_3zDaTEQd8KrynXmsDzcFNwXBxZMXpa7wCHVraIgId8aMqKPqCUxlnla_IAXncfS2ioSzaQsoMdrJNvDe-g.5tZEkfFc1scvThad.fwwVxaVawK7NUyQsfLfEsfG4HKJ3-LSl2kpCPh2AnrFpe4wWGi0FECMJ99zWY8UbLJyrhUYDtTfORh0Q2WblTj6V8A7LHH75fQ6Srz_uipX2N2yqGC9T5bXQsyUzmM4QwB25c1wHerGM8bT_2fBEhbK7W76xOZ_4rdJpmWKlgXm_5xBMTAv7LuuuY2_FtkwjSOq3W9j37ecV6cyxF4WPKFdb0uk4gD5OpXGlkhAZHZSNLQv_EGGQyRqnm2JqfhNPC_wI8R9b9L2MyoBqHvPWUNBJGLw3Nus41Qkl8PcIQv6Ek8vHeZSrEY6GjgAKS0uKYm1st5VUnbsBJAkG9IWEVbIStNvYzECcB0wdaIksqYuus9Lub_mb_a3x3-3IadsNAOj2Bd0CLx2VkzfMi9hegf9mjcv5cNLgHDpoyuF3dXc7IgvlBKJFyQdCPq8fKitzyEB4yAR4VyTGPA2DDxfh33czlCDrQbA2BHeBCrDcByqmRJsiRL3pMMxNc-qqdCa1ylmzKPol7CeWykiEoA.Ykg4PaK43r0lMOUJBWvtiA"
    },
    "browserInfo": {
      "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
      "acceptHeader": "*/*",
      "language": "en-US",
      "colorDepth": 24,
      "screenHeight": 900,
      "screenWidth": 1440,
      "timeZoneOffset": 0,
      "javaEnabled": false
    }
  }'
```

**Adjust authorisation**
```bash
curl -X POST http://localhost:8080/api/modify-amount \
  -H "Content-Type: application/json" \
  -d '{
    "pspReference": "CWN94TT4XG664Q75",
    "reference": "adjust-demo-001",
    "amount": { "currency": "EUR", "value": 5499 },
    "industryUsage": "delayedCharge"
  }'
```

**Capture**
```bash
curl -X POST http://localhost:8080/api/capture \
  -H "Content-Type: application/json" \
  -d '{
    "pspReference": "CWN94TT4XG664Q75",
    "reference": "capture-demo-001",
    "amount": { "currency": "EUR", "value": 5499 }
  }'
```

**Cancel**
```bash
curl -X POST http://localhost:8080/api/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "pspReference": "<authorised-pspReference>",
    "reference": "cancel-demo-001"
  }'
```

**Refund**
```bash
curl -X POST http://localhost:8080/api/refund \
  -H "Content-Type: application/json" \
  -d '{
    "pspReference": "<captured-pspReference>",
    "reference": "refund-demo-001",
    "amount": { "currency": "EUR", "value": 3000 }
  }'
```
