const clientKey = document.getElementById("clientKey").innerHTML;
const { AdyenCheckout, Dropin } = window.AdyenWeb;

// Starts the (Adyen.Web) AdyenCheckout with your specified configuration by calling the `/paymentMethods` endpoint.
async function startCheckout() {
    try {
        // Step 8 - Retrieve the available payment methods
        const paymentMethodsResponse = await fetch("/api/paymentMethods", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            }
        }).then(response => response.json());

        const configuration = {
            paymentMethodsResponse: paymentMethodsResponse,
            clientKey,
            locale: "en_US",
            countryCode: 'NL',
            environment: "test",
            showPayButton: true,
            translations: {
                'en-US': {
                    'creditCard.securityCode.label': 'CVV/CVC'
                }
            },
            onSubmit: async (state, component, actions) => {
                console.info("onSubmit", state, component, actions);
                try {
                    if (state.isValid) {
                        const payload = {
                            ...state.data,
                            storePaymentMethod: true,
                            shopperReference: "subscription-shopper"
                        };

                        const { action, order, resultCode } = await fetch("/api/subscription-create", {
                            method: "POST",
                            body: JSON.stringify(payload),
                            headers: {
                                "Content-Type": "application/json",
                            }
                        }).then(response => response.json());

                        if (!resultCode) {
                            console.warn("reject");
                            actions.reject();
                        }

                        actions.resolve({
                            resultCode,
                            action,
                            order
                        });
                    }
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            },
            onPaymentCompleted: (result, component) => {
                console.info("onPaymentCompleted", result, component);
                handleOnPaymentCompleted(result, component);
            },
            onPaymentFailed: (result, component) => {
                console.info("onPaymentFailed", result, component);
                handleOnPaymentFailed(result, component);
            },
            onError: (error, component) => {
                console.error("onError", error.name, error.message, error.stack, component);
                window.location.href = "/result/error";
            },
            onAdditionalDetails: async (state, component, actions) => {
                console.info("onAdditionalDetails", state, component);
                try {
                    const { resultCode } = await fetch("/api/payments/details", {
                        method: "POST",
                        body: state.data ? JSON.stringify(state.data) : "",
                        headers: {
                            "Content-Type": "application/json",
                        }
                    }).then(response => response.json());

                    if (!resultCode) {
                        console.warn("reject");
                        actions.reject();
                    }

                    actions.resolve({ resultCode });
                } catch (error) {
                    console.error(error);
                    actions.reject();
                }
            }
        };

        const paymentMethodsConfiguration = {
            card: {
                showBrandIcon: true,
                hasHolderName: true,
                holderNameRequired: true,
                name: "Credit or debit card",
                // Enable the toggle to store card details (tokenization)
                enableStoreDetails: true,
                showStorePaymentMethodButton: true,
                amount: {
                    value: 0,
                    currency: "EUR",
                },
                placeholders: {
                    cardNumber: '1234 5678 9012 3456',
                    expiryDate: 'MM/YY',
                    securityCodeThreeDigits: '123',
                    securityCodeFourDigits: '1234',
                    holderName: 'Developer Relations Team'
                }
            }
        };

        // Start the AdyenCheckout and mount the element onto the `payment`-div.
        const adyenCheckout = await AdyenCheckout(configuration);
        const dropin = new Dropin(adyenCheckout, { paymentMethodsConfiguration: paymentMethodsConfiguration }).mount(document.getElementById("payment"));
    } catch (error) {
        console.error(error);
        alert("Error occurred. Look at console for details.");
    }
}

// Step 10 - Function to handle payment completion redirects
function handleOnPaymentCompleted(response) {
    if (response.pspReference) {
        console.info(`Payment authorised with pspReference=${response.pspReference}. Use this in curl calls for adjust/capture/refund.`);
        window.lastPspReference = response.pspReference;
    }
    switch (response.resultCode) {
        case "Authorised":
            window.location.href = "/result/success";
            break;
        case "Pending":
        case "Received":
            window.location.href = "/result/pending";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

// Step 10 - Function to handle payment failure redirects
function handleOnPaymentFailed(response) {
    switch (response.resultCode) {
        case "Cancelled":
        case "Refused":
            window.location.href = "/result/failed";
            break;
        default:
            window.location.href = "/result/error";
            break;
    }
}

// Utility to quickly see incoming webhooks while testing the preauthorisation flow.
async function fetchRecentWebhooks() {
    try {
        const events = await fetch("/api/webhooks/recent").then(response => response.json());
        console.table(events);
        return events;
    } catch (error) {
        console.error("Unable to fetch recent webhooks", error);
        return [];
    }
}

// Expose helper for manual testing in the browser console
window.fetchRecentWebhooks = fetchRecentWebhooks;

startCheckout();
