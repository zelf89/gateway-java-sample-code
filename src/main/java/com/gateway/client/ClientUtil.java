package com.gateway.client;

import com.gateway.app.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.commons.lang.RandomStringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ClientUtil {

    public static String getRequestUrl(Config config, ApiRequest request) {
        String url = config.getGatewayHost() + "/version/" + config.getApiVersion() + "/merchant/" + config.getMerchantId() + "/order/" + request.getOrderId();
        if(notNullOrEmpty(request.getTransactionId())) {
            url += "/transaction/" + request.getTransactionId();
        }
        return url;
    }

    public static String getSessionRequestUrl(Config config) {
        return config.getGatewayHost() + "/version/" + config.getApiVersion() + "/merchant/" + config.getMerchantId() + "/session";
    }

    public static String getSessionRequestUrl(Config config, String sessionId) {
        return config.getGatewayHost() + "/version/" + config.getApiVersion() + "/merchant/" + config.getMerchantId() + "/session/" + sessionId;
    }

    public static String getSecureIdRequest(Config config, String secureId) {
        return config.getGatewayHost() + "/version/" + config.getApiVersion() + "/merchant/" + config.getMerchantId() + "/3DSecureId/" + secureId;
    }

    public static String buildJSONPayload(ApiRequest request) {
        JsonObject order = new JsonObject();

        JsonObject secureId = new JsonObject();
        if(notNullOrEmpty(request.getPaymentAuthResponse())) {
            secureId.addProperty("paRes", request.getPaymentAuthResponse());
        }

        JsonObject authenticationRedirect = new JsonObject();
        if (notNullOrEmpty(request.getSecureIdResponseUrl())) {
            authenticationRedirect.addProperty("responseUrl", request.getSecureIdResponseUrl());
            secureId.add("authenticationRedirect", authenticationRedirect);
        }

        if (request.getApiOperation().equals("CREATE_CHECKOUT_SESSION")) {
            // Need to add order ID in the request body for CREATE_CHECKOUT_SESSION. Its presence in the body will cause an error for the other operations.
            if (notNullOrEmpty(request.getOrderId())) order.addProperty("id", request.getOrderId());
        }
        if (notNullOrEmpty(request.getOrderAmount())) order.addProperty("amount", request.getOrderAmount());
        if (notNullOrEmpty(request.getOrderCurrency())) order.addProperty("currency", request.getOrderCurrency());

        JsonObject transaction = new JsonObject();
        if (notNullOrEmpty(request.getTransactionAmount()))
            transaction.addProperty("amount", request.getTransactionAmount());
        if (notNullOrEmpty(request.getTransactionCurrency()))
            transaction.addProperty("currency", request.getTransactionCurrency());
        if (notNullOrEmpty(request.getTargetTransactionId()))
            transaction.addProperty("targetTransactionId", request.getTargetTransactionId());

        JsonObject expiry = new JsonObject();
        if (notNullOrEmpty(request.getExpiryMonth())) expiry.addProperty("month", request.getExpiryMonth());
        if (notNullOrEmpty(request.getExpiryYear())) expiry.addProperty("year", request.getExpiryYear());

        JsonObject card = new JsonObject();
        if (notNullOrEmpty(request.getSecurityCode())) card.addProperty("securityCode", request.getSecurityCode());
        if (notNullOrEmpty(request.getCardNumber())) card.addProperty("number", request.getCardNumber());
        if (!expiry.entrySet().isEmpty()) card.add("expiry", expiry);

        JsonObject provided = new JsonObject();
        if (!card.entrySet().isEmpty()) provided.add("card", card);

        JsonObject sourceOfFunds = new JsonObject();
        if (notNullOrEmpty(request.getSourceType())) sourceOfFunds.addProperty("type", request.getSourceType());
        if (!provided.entrySet().isEmpty()) sourceOfFunds.add("provided", provided);

        JsonObject browserPayment = new JsonObject();
        if (notNullOrEmpty(request.getBrowserPaymentOperation()))
            browserPayment.addProperty("operation", request.getBrowserPaymentOperation());
        if (notNullOrEmpty(request.getSourceType())) addPaymentConfirmation(browserPayment, request);

        JsonObject interaction = new JsonObject();
        if (notNullOrEmpty(request.getReturnUrl())) {
            // Return URL needs to be added differently for browser payments and hosted checkout payments
            if (request.getApiOperation().equals("CREATE_CHECKOUT_SESSION")) {
                interaction.addProperty("returnUrl", request.getReturnUrl());
            } else if (request.getApiOperation().equals("INITIATE_BROWSER_PAYMENT") || request.getApiOperation().equals("CONFIRM_BROWSER_PAYMENT")) {
                browserPayment.addProperty("returnUrl", request.getReturnUrl());
            }
        }

        JsonObject session = new JsonObject();
        if (notNullOrEmpty(request.getSessionId())) session.addProperty("id", request.getSessionId());

        // Add all the elements to the main JSON object we'll return from this method
        JsonObject data = new JsonObject();
        if (notNullOrEmpty(request.getApiOperation())) data.addProperty("apiOperation", request.getApiOperation());
        if (!order.entrySet().isEmpty()) data.add("order", order);
        if (!transaction.entrySet().isEmpty()) data.add("transaction", transaction);
        if (!sourceOfFunds.entrySet().isEmpty()) data.add("sourceOfFunds", sourceOfFunds);
        if (!browserPayment.entrySet().isEmpty()) data.add("browserPayment", browserPayment);
        if (!interaction.entrySet().isEmpty()) data.add("interaction", interaction);
        if (!session.entrySet().isEmpty()) data.add("session", session);
        if (!secureId.entrySet().isEmpty()) data.add("3DSecure", secureId);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        System.out.println("$$$$$$$$$$$$$$$ DATA: " + data);
        return gson.toJson(data);
    }

    public static CheckoutSession parseSessionResponse(String sessionResponse) {
        JsonObject json = new Gson().fromJson(sessionResponse, JsonObject.class);
        JsonObject jsonSession = json.get("session").getAsJsonObject();

        CheckoutSession checkoutSession = new CheckoutSession();
        checkoutSession.setId(jsonSession.get("id").getAsString());
        checkoutSession.setVersion(jsonSession.get("version").getAsString());
        if(json.get("successIndicator") != null) checkoutSession.setSuccessIndicator(json.get("successIndicator").getAsString());

        return checkoutSession;
    }

    public static String encodeUrl(String url) {
        String encodedUrl = "";

        try {
            encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
        }
        catch(UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return encodedUrl;
    }

    public static String randomNumber() {
        return RandomStringUtils.random(10, true, true);
    }

    private static boolean notNullOrEmpty(String value) {
        return (value != null && !value.equals(""));
    }

    private static void addPaymentConfirmation(JsonObject browserPayment, ApiRequest request) {
        switch (request.getSourceType().toUpperCase()) {
            case "ALIPAY":
                JsonObject alipay = new JsonObject();
                alipay.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("alipay", alipay);
                break;
            case "BANCANET":
                JsonObject bancanet = new JsonObject();
                bancanet.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("bancanet", bancanet);
                break;
            case "GIROPAY":
                JsonObject giropay = new JsonObject();
                giropay.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("giropay", giropay);
                break;
            case "IDEAL":
                JsonObject ideal = new JsonObject();
                ideal.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("ideal", ideal);
                break;
            case "MULTIBANCO":
                JsonObject multibanco = new JsonObject();
                multibanco.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("multibanco", multibanco);
                break;
            case "PAYPAL":
                JsonObject paypal = new JsonObject();
                paypal.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("paypal", paypal);
                break;
            case "SOFORT":
                JsonObject sofort = new JsonObject();
                sofort.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("sofort", sofort);
                break;
            case "UNION_PAY":
                JsonObject unionpay = new JsonObject();
                unionpay.addProperty("paymentConfirmation", "CONFIRM_AT_PROVIDER");
                browserPayment.add("unionpay", unionpay);
                break;
        }
    }
}
