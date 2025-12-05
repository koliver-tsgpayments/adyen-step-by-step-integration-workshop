package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.SignatureException;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
public class WebhookController {
    private final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ApplicationConfiguration applicationConfiguration;

    private final HMACValidator hmacValidator;

    @Autowired
    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
    }

    // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        log.info("Received: {}", json);
        NotificationRequest notificationRequest;
        try {
            notificationRequest = NotificationRequest.fromJson(json);
        } catch (Exception parseEx) {
            log.warn("Unable to parse webhook payload as classic notification, trying management events", parseEx);
            notificationRequest = null;
        }
        // Handle classic notification payload
        if (notificationRequest != null && notificationRequest.getNotificationItems() != null) {
            try {
                for (NotificationRequestItem item : notificationRequest.getNotificationItems()) {

                    // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
                    if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
                        log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
                        return ResponseEntity.unprocessableEntity().build();
                    }

                    var eventCode = item.getEventCode();
                    var success = item.isSuccess();
                    var merchantReference = item.getMerchantReference();
                    var pspReference = item.getPspReference();
                    var additionalData = item.getAdditionalData();
                    // Token can show up under different keys
                    String storedPaymentMethodId = additionalData != null
                            ? (additionalData.get("tokenization.storedPaymentMethodId") != null
                                ? additionalData.get("tokenization.storedPaymentMethodId")
                                : additionalData.get("storedPaymentMethodId"))
                            : null;
                    String recurringDetailReference = additionalData != null
                            ? additionalData.get("recurring.recurringDetailReference")
                            : null;

                    // Log any classic notification with token info if present
                    log.info("Webhook eventCode={}, success={}, merchantRef={}, pspRef={}, token={}, storedPaymentMethodId={}",
                            eventCode, success, merchantReference, pspReference, recurringDetailReference, storedPaymentMethodId);
                }

                return ResponseEntity.ok("[accepted]");
            } catch (SignatureException e) {
                // Handle invalid signature
                return ResponseEntity.unprocessableEntity().build();
            } catch (Exception e) {
                // Handle all other errors
                return ResponseEntity.status(500).build();
            }
        }

        // Handle newer management event payloads (e.g. recurring.token.created)
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            var type = node.path("type").asText();
            if ("recurring.token.created".equalsIgnoreCase(type)) {
                var dataNode = node.path("data");
                var merchantAccount = dataNode.path("merchantAccount").asText();
                var shopperReference = dataNode.path("shopperReference").asText();
                var storedPaymentMethodId = dataNode.path("storedPaymentMethodId").asText();
                var tokenType = dataNode.path("type").asText();
                log.info("Recurring token created event received. merchantAccount={}, shopperReference={}, storedPaymentMethodId={}, type={}",
                        merchantAccount, shopperReference, storedPaymentMethodId, tokenType);
                return ResponseEntity.ok("[accepted]");
            }
        } catch (Exception e) {
            log.warn("Unable to parse management event payload", e);
            return ResponseEntity.unprocessableEntity().build();
        }

        log.warn("Webhook payload is missing notification items");
        return ResponseEntity.unprocessableEntity().build();
    }
}
