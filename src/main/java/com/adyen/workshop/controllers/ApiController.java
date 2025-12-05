package com.adyen.workshop.controllers;

import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.checkout.RecurringApi;
import com.adyen.service.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for using the Adyen payments API.
 */
@RestController
public class ApiController {
    private final Logger log = LoggerFactory.getLogger(ApiController.class);
    private static final String SHOPPER_REFERENCE = "KevinOliver";

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final ModificationsApi modificationsApi;
    private final RecurringApi recurringApi;

    public ApiController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, ModificationsApi modificationsApi, RecurringApi recurringApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.modificationsApi = modificationsApi;
        this.recurringApi = recurringApi;
    }

    // Step 0
    @GetMapping("/hello-world")
    public ResponseEntity<String> helloWorld() throws Exception {
        return ResponseEntity.ok().body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
    }

    // Step 7
    @PostMapping("/api/paymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        var paymentMethodsRequest = new PaymentMethodsRequest();
        paymentMethodsRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentMethodsRequest.shopperReference(SHOPPER_REFERENCE);

        log.info("Retrieving available Payment Methods from Adyen {}", paymentMethodsRequest);
        var response = paymentsApi.paymentMethods(paymentMethodsRequest);
        log.info("Payment Methods response from Adyen {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 9 - Implement the /payments call to Adyen.
    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> payments(@RequestHeader String host, @RequestBody PaymentRequest body, HttpServletRequest request) throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();

        var amount = new Amount()
                .currency("EUR")
                .value(9998L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);

        paymentRequest.setPaymentMethod(body.getPaymentMethod());

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        // The returnUrl field basically means: Once done with the payment, where should the application redirect you?
        paymentRequest.setReturnUrl(request.getScheme() + "://" + host + "/handleShopperRedirect?orderRef=" + orderRef); // Example: Turns into http://localhost:8080/handleShopperRedirect?orderRef=354fa90e-0858-4d2f-92b9-717cb8e18173

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        // // Step 12 3DS2 Redirect - Add the following additional parameters to your existing payment request for 3DS2 Redirect:
        // // Note: Visa requires additional properties to be sent in the request, see documentation for Redirect 3DS2: https://docs.adyen.com/online-payments/3d-secure/redirect-3ds2/web-drop-in/#make-a-payment
        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        
        // Add the following lines, if you want to enable the Native 3DS2 flow:
        // Note: Visa requires additional properties to be sent in the request, see documentation for Native 3DS2: https://docs.adyen.com/online-payments/3d-secure/native-3ds2/web-drop-in/#make-a-payment
        authenticationData.setThreeDSRequestData(new ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setOrigin(request.getScheme() + "://" + host);
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP(request.getRemoteAddr());
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        log.info("PaymentsRequest {}", paymentRequest, requestOptions);
        var response = paymentsApi.payments(paymentRequest);
        log.info("PaymentsResponse {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 1 Tokenization - Create a zero-auth payment to store card details (recurringDetailReference returned via webhook)
    @PostMapping("/api/subscription-create")
    public ResponseEntity<PaymentResponse> subscriptionCreate(@RequestHeader String host, @RequestBody PaymentRequest body, HttpServletRequest request) throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();

        var amount = new Amount()
                .currency("EUR")
                .value(0L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);

        paymentRequest.setPaymentMethod(body.getPaymentMethod());

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl(request.getScheme() + "://" + host + "/handleShopperRedirect?orderRef=" + orderRef);

        paymentRequest.setStorePaymentMethod(true);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);
        // Always use a fixed shopper reference for tokenization in this workshop scenario
        paymentRequest.setShopperReference(SHOPPER_REFERENCE);
        paymentRequest.setOrigin(request.getScheme() + "://" + host);
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP(request.getRemoteAddr());

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        authenticationData.setThreeDSRequestData(new ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
        paymentRequest.setAuthenticationData(authenticationData);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription tokenization request {}", paymentRequest, requestOptions);
        var response = paymentsApi.payments(paymentRequest);
        log.info("Subscription tokenization response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 3 Tokenization - Charge once using a previously stored payment method token
    @PostMapping("/api/subscription-payment")
    public ResponseEntity<PaymentResponse> subscriptionPayment(@RequestBody Map<String, String> body) throws IOException, ApiException {
        // Example curl: curl -X POST http://localhost:8080/api/subscription-payment -H "Content-Type: application/json" -d '{"storedPaymentMethodId":"<TOKEN>"}'
        var storedPaymentMethodId = body.get("storedPaymentMethodId");
        if (storedPaymentMethodId == null || storedPaymentMethodId.isBlank()) {
            log.warn("Subscription payment requested without storedPaymentMethodId");
            return ResponseEntity.badRequest().build();
        }

        var paymentRequest = new PaymentRequest();
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        var amount = new Amount()
                .currency("EUR")
                .value(500L);
        paymentRequest.setAmount(amount);

        var cardDetails = new CardDetails();
        cardDetails.setType(CardDetails.TypeEnum.SCHEME);
        cardDetails.setStoredPaymentMethodId(storedPaymentMethodId);
        paymentRequest.setPaymentMethod(new CheckoutPaymentMethod(cardDetails));

        paymentRequest.setShopperReference(SHOPPER_REFERENCE);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        paymentRequest.setReference("subscription-payment-" + UUID.randomUUID());

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription payment request {}", paymentRequest, requestOptions);
        var response = paymentsApi.payments(paymentRequest);
        log.info("Subscription payment response {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 4 Tokenization - Delete a stored payment method token for the fixed shopper
    @PostMapping("/api/subscriptions-cancel")
    public ResponseEntity<Void> subscriptionCancel(@RequestBody Map<String, String> body) throws IOException, ApiException {
        // Example curl: curl -X POST http://localhost:8080/api/subscriptions-cancel -H "Content-Type: application/json" -d '{"storedPaymentMethodId":"<TOKEN>"}'
        var storedPaymentMethodId = body.get("storedPaymentMethodId");
        if (storedPaymentMethodId == null || storedPaymentMethodId.isBlank()) {
            log.warn("Subscription cancel requested without storedPaymentMethodId");
            return ResponseEntity.badRequest().build();
        }

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Deleting stored payment method {} for shopper {}", storedPaymentMethodId, SHOPPER_REFERENCE);
        recurringApi.deleteTokenForStoredPaymentDetails(
                storedPaymentMethodId,
                SHOPPER_REFERENCE,
                applicationConfiguration.getAdyenMerchantAccount(),
                requestOptions);

        return ResponseEntity.noContent().build();
    }

    // Step 13 - Handle details call (triggered after Native 3DS2 flow)
    @PostMapping("/api/payments/details")
    public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest) throws IOException, ApiException
    {
        log.info("PaymentDetailsRequest {}", detailsRequest);
        var response = paymentsApi.paymentsDetails(detailsRequest);
        log.info("PaymentDetailsResponse {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Preauthorisation step - create an authorisation that can be modified/captured later
    @PostMapping("/api/preauthorisation")
    public ResponseEntity<PaymentResponse> preauthorisation(@RequestHeader String host, @RequestBody PaymentRequest body, HttpServletRequest request) throws IOException, ApiException {
        if (body.getPaymentMethod() == null) {
            log.warn("Preauthorisation requested without paymentMethod details");
            return ResponseEntity.badRequest().build();
        }

        var paymentRequest = new PaymentRequest();
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());
        paymentRequest.setAmount(body.getAmount() != null ? body.getAmount() : new Amount().currency("EUR").value(4999L));

        var orderRef = (body.getReference() != null && !body.getReference().isBlank())
                ? body.getReference()
                : UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl(request.getScheme() + "://" + host + "/handleShopperRedirect?orderRef=" + orderRef);

        paymentRequest.setOrigin(request.getScheme() + "://" + host);
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP(request.getRemoteAddr());
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);
        // If a stored token is used, set recurring model + shopperReference as required by Adyen
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setShopperReference(SHOPPER_REFERENCE);

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        authenticationData.setThreeDSRequestData(new ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
        paymentRequest.setAuthenticationData(authenticationData);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Preauthorisation request {}", paymentRequest, requestOptions);
        var response = paymentsApi.payments(paymentRequest);
        log.info("Preauthorisation response {}", response);
        return ResponseEntity.ok(response);
    }

    // Adjust authorised amount (preauthorisation adjust)
    @PostMapping("/api/modify-amount")
    public ResponseEntity<PaymentAmountUpdateResponse> modifyAmount(@RequestBody Map<String, Object> body) throws IOException, ApiException {
        var pspReference = (String) body.get("pspReference");
        var amountMap = safeMap(body.get("amount"));
        if (pspReference == null || pspReference.isBlank() || amountMap == null) {
            log.warn("Modify amount requested without required fields");
            return ResponseEntity.badRequest().build();
        }

        var amount = amountFromMap(amountMap, 0L);
        if (amount == null) {
            return ResponseEntity.badRequest().build();
        }

        var paymentAmountUpdateRequest = new PaymentAmountUpdateRequest();
        paymentAmountUpdateRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentAmountUpdateRequest.setReference(body.getOrDefault("reference", "adjust-" + UUID.randomUUID()).toString());
        paymentAmountUpdateRequest.setAmount(amount);

        var industryUsage = body.get("industryUsage");
        if (industryUsage instanceof String industryUsageString && !industryUsageString.isBlank()) {
            try {
                paymentAmountUpdateRequest.setIndustryUsage(PaymentAmountUpdateRequest.IndustryUsageEnum.fromValue(industryUsageString));
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid industryUsage provided: {}", industryUsageString);
                return ResponseEntity.badRequest().build();
            }
        }

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Modify amount request for {} {}", pspReference, paymentAmountUpdateRequest, requestOptions);
        var response = modificationsApi.updateAuthorisedAmount(pspReference, paymentAmountUpdateRequest, requestOptions);
        log.info("Modify amount response {}", response);
        return ResponseEntity.ok(response);
    }

    // Capture the authorised payment
    @PostMapping("/api/capture")
    public ResponseEntity<PaymentCaptureResponse> capture(@RequestBody Map<String, Object> body) throws IOException, ApiException {
        var pspReference = (String) body.get("pspReference");
        var amountMap = safeMap(body.get("amount"));
        if (pspReference == null || pspReference.isBlank() || amountMap == null) {
            log.warn("Capture requested without required fields");
            return ResponseEntity.badRequest().build();
        }

        var amount = amountFromMap(amountMap, null);
        if (amount == null) {
            return ResponseEntity.badRequest().build();
        }

        var captureRequest = new PaymentCaptureRequest();
        captureRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        captureRequest.setReference(body.getOrDefault("reference", "capture-" + UUID.randomUUID()).toString());
        captureRequest.setAmount(amount);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Capture request for {} {}", pspReference, captureRequest, requestOptions);
        var response = modificationsApi.captureAuthorisedPayment(pspReference, captureRequest, requestOptions);
        log.info("Capture response {}", response);
        return ResponseEntity.ok(response);
    }

    // Cancel the authorised payment
    @PostMapping("/api/cancel")
    public ResponseEntity<PaymentCancelResponse> cancel(@RequestBody Map<String, Object> body) throws IOException, ApiException {
        var pspReference = (String) body.get("pspReference");
        if (pspReference == null || pspReference.isBlank()) {
            log.warn("Cancel requested without pspReference");
            return ResponseEntity.badRequest().build();
        }

        var cancelRequest = new PaymentCancelRequest();
        cancelRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        cancelRequest.setReference(body.getOrDefault("reference", "cancel-" + UUID.randomUUID()).toString());

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Cancel request for {} {}", pspReference, cancelRequest, requestOptions);
        var response = modificationsApi.cancelAuthorisedPaymentByPspReference(pspReference, cancelRequest, requestOptions);
        log.info("Cancel response {}", response);
        return ResponseEntity.ok(response);
    }

    // Refund after capture
    @PostMapping("/api/refund")
    public ResponseEntity<PaymentRefundResponse> refund(@RequestBody Map<String, Object> body) throws IOException, ApiException {
        var pspReference = (String) body.get("pspReference");
        var amountMap = safeMap(body.get("amount"));
        if (pspReference == null || pspReference.isBlank() || amountMap == null) {
            log.warn("Refund requested without required fields");
            return ResponseEntity.badRequest().build();
        }

        var amount = amountFromMap(amountMap, null);
        if (amount == null) {
            return ResponseEntity.badRequest().build();
        }

        var refundRequest = new PaymentRefundRequest();
        refundRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        refundRequest.setReference(body.getOrDefault("reference", "refund-" + UUID.randomUUID()).toString());
        refundRequest.setAmount(amount);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Refund request for {} {}", pspReference, refundRequest, requestOptions);
        var response = modificationsApi.refundCapturedPayment(pspReference, refundRequest, requestOptions);
        log.info("Refund response {}", response);
        return ResponseEntity.ok(response);
    }


    // Step 14 - Handle Redirect 3DS2 during payment.
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload, @RequestParam(required = false) String redirectResult) throws IOException, ApiException {

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private Amount amountFromMap(Map<String, Object> amountMap, Long fallbackValue) {
        if (amountMap == null) {
            return null;
        }
        var currency = amountMap.getOrDefault("currency", "EUR").toString();
        var valueObj = amountMap.get("value");
        Long value = null;
        if (valueObj instanceof Number number) {
            value = number.longValue();
        } else if (valueObj instanceof String stringValue && !stringValue.isBlank()) {
            try {
                value = Long.parseLong(stringValue);
            } catch (NumberFormatException e) {
                log.warn("Unable to parse amount value {}", stringValue);
                return null;
            }
        }

        if (value == null) {
            value = fallbackValue;
        }

        if (value == null) {
            log.warn("Amount value missing for request");
            return null;
        }

        var amount = new Amount();
        amount.setCurrency(currency);
        amount.setValue(value);
        return amount;
    }
}
