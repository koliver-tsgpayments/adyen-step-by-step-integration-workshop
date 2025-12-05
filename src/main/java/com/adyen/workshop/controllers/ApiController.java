package com.adyen.workshop.controllers;

import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.workshop.configurations.ApplicationConfiguration;
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
    private final RecurringApi recurringApi;

    public ApiController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, RecurringApi recurringApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
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


    // Step 14 - Handle Redirect 3DS2 during payment.
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload, @RequestParam(required = false) String redirectResult) throws IOException, ApiException {

        return null;
    }
}
