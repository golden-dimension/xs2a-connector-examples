package de.adorsys.ledgers.xs2a.test.ctk.pis;

import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.springframework.http.ResponseEntity;

import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.oba.rest.api.domain.AuthorizeResponse;
import de.adorsys.ledgers.oba.rest.api.domain.PaymentAuthorizeResponse;
import de.adorsys.ledgers.oba.rest.client.ObaPisApiClient;
import de.adorsys.ledgers.xs2a.api.client.PaymentApiClient;
import de.adorsys.psd2.model.PaymentInitationRequestResponse201;
import de.adorsys.psd2.model.PaymentInitiationStatusResponse200Json;

public class PaymentExecutionHelper {

	private final String digest = null;
	private final String signature = null;
	private final byte[] tpPSignatureCertificate = null;
	private final String psUIDType = null;
	private final String psUCorporateID = null;
	private final String psUCorporateIDType = null;
	private final String psUIPAddress = "127.0.0.1";
	private final String psUIPPort = null;
	private final String psUAccept = null;
	private final String psUAcceptCharset = null;
	private final String psUAcceptEncoding = null;
	private final String psUAcceptLanguage = null;
	private final String psUUserAgent = null;
	private final String psUHttpMethod = null;
	private final UUID psUDeviceID = UUID.randomUUID();
	private final String psUGeoLocation = null;

	private final PaymentApiClient paymentApi;
	private final ObaPisApiClient pisApiClient;
	private final PaymentCase paymentCase;
	private final String paymentService;
	private final String paymentProduct;

	public PaymentExecutionHelper(PaymentApiClient paymentApi, ObaPisApiClient pisApiClient, PaymentCase paymentCase,
			String paymentService, String paymentProduct) {
		super();
		this.paymentApi = paymentApi;
		this.pisApiClient = pisApiClient;
		this.paymentCase = paymentCase;
		this.paymentService = paymentService;
		this.paymentProduct = paymentProduct;
	}

	public PaymentInitationRequestResponse201 initiatePayment() {
		Object payment = paymentCase.getPayment();
		UUID xRequestID = UUID.randomUUID();
		String PSU_ID = paymentCase.getPsuId();
		String consentID = null;
		String tpPRedirectPreferred = "true";
		String tpPRedirectURI = null;
		String tpPNokRedirectURI = null;
		Boolean tpPExplicitAuthorisationPreferred = true;
		PaymentInitationRequestResponse201 initiatedPayment = paymentApi._initiatePayment(payment, xRequestID, psUIPAddress,
				paymentService, paymentProduct, digest, signature, tpPSignatureCertificate, PSU_ID, psUIDType,
				psUCorporateID, psUCorporateIDType, consentID, tpPRedirectPreferred, tpPRedirectURI, tpPNokRedirectURI,
				tpPExplicitAuthorisationPreferred, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding,
				psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation).getBody();

		Assert.assertNotNull(initiatedPayment);
		Assert.assertNotNull(getScaRedirect(initiatedPayment));

		Assert.assertNotNull(initiatedPayment.getPaymentId());
		Assert.assertNotNull(initiatedPayment.getTransactionStatus());
		Assert.assertEquals("RCVD", initiatedPayment.getTransactionStatus().name());
		Assert.assertNotNull(initiatedPayment.getPaymentId());

		return initiatedPayment;
	}

	static class RedirectedParams {
		private final String redirectId;
		private final String encryptedPaymentId;
		public RedirectedParams(String scaRedirect) {
			encryptedPaymentId = StringUtils.substringBetween(scaRedirect, "paymentId=", "&redirectId=");
			redirectId = StringUtils.substringAfter(scaRedirect, "&redirectId=");
		}
		public String getRedirectId() {
			return redirectId;
		}
		public String getEncryptedPaymentId() {
			return encryptedPaymentId;
		}
	}
	public ResponseEntity<PaymentAuthorizeResponse> login(PaymentInitationRequestResponse201 initiatedPayment) throws MalformedURLException {
		String scaRedirectLink = getScaRedirect(initiatedPayment);
		String encryptedPaymentId = initiatedPayment.getPaymentId();
		String redirectId = QuerryParser.param(scaRedirectLink, "redirectId");
		String encryptedPaymentIdFromOnlineBanking = QuerryParser.param(scaRedirectLink, "paymentId");
		
		Assert.assertEquals(encryptedPaymentId, encryptedPaymentIdFromOnlineBanking);
		
		ResponseEntity<AuthorizeResponse> pisAuth = pisApiClient.pisAuth(redirectId, encryptedPaymentId);
		URI location = pisAuth.getHeaders().getLocation();
		String scaId = QuerryParser.param(location.toString(), "scaId");
		String authorisationId = QuerryParser.param(location.toString(), "authorisationId");
		List<String> cookieStrings = pisAuth.getHeaders().get("Set-Cookie");
		String consentCookieString = readCookie(cookieStrings, "CONSENT");
		ResponseEntity<PaymentAuthorizeResponse> loginResponse = pisApiClient.login(scaId, authorisationId, paymentCase.getPsuId(), "12345", resetCookies(cookieStrings));
		
		Assert.assertNotNull(loginResponse);
		Assert.assertTrue(loginResponse.getStatusCode().is2xxSuccessful());
		cookieStrings = loginResponse.getHeaders().get("Set-Cookie");
		consentCookieString = readCookie(cookieStrings, "CONSENT");
		Assert.assertNotNull(consentCookieString);
		String accessTokenCookieString = readCookie(cookieStrings, "ACCESS_TOKEN");
		Assert.assertNotNull(accessTokenCookieString);

		return loginResponse;
	}

	private String readCookie(List<String> cookieHeaders, String cookieName) {
		for (String httpCookie : cookieHeaders) {
			if(StringUtils.startsWithIgnoreCase(httpCookie.trim(), cookieName)){
				return httpCookie.trim();
			}
		}
		return null;
	}
	
	
	private String resetCookies(List<String> cookieStrings) {
		String result = null;
		for (String cookieString : cookieStrings) {
			List<HttpCookie> parse = HttpCookie.parse(cookieString);
			for (HttpCookie httpCookie : parse) {
				if(StringUtils.isNoneBlank(httpCookie.getValue())) {
					String cookie = httpCookie.getName()+"="+httpCookie.getValue();
					if(result==null) {
						result = cookie;
					} else {
						result = result + " ; " + cookie;
					}
				}
			}
		}
		return result;
	}


	public PaymentInitiationStatusResponse200Json loadPaymentStatus(String encryptedPaymentId) {
		UUID xRequestID = UUID.randomUUID();
		PaymentInitiationStatusResponse200Json paymentInitiationStatus = paymentApi
				._getPaymentInitiationStatus(paymentService, paymentProduct, encryptedPaymentId, xRequestID, digest, signature,
						tpPSignatureCertificate, psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset,
						psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation)
				.getBody();

		Assert.assertNotNull(paymentInitiationStatus);

		return paymentInitiationStatus;
	}

	private String getScaRedirect(PaymentInitationRequestResponse201 resp) {
		return (String) resp.getLinks().get("scaRedirect");
	}

	public ResponseEntity<PaymentAuthorizeResponse> authCode(ResponseEntity<PaymentAuthorizeResponse> paymentResponse) {
		Assert.assertNotNull(paymentResponse);
		Assert.assertTrue(paymentResponse.getStatusCode().is2xxSuccessful());
		List<String> cookieStrings = paymentResponse.getHeaders().get("Set-Cookie");
		String consentCookieString = readCookie(cookieStrings, "CONSENT");
		Assert.assertNotNull(consentCookieString);
		String accessTokenCookieString = readCookie(cookieStrings, "ACCESS_TOKEN");
		Assert.assertNotNull(accessTokenCookieString);

		PaymentAuthorizeResponse paymentAuthorizeResponse = paymentResponse.getBody();
		ResponseEntity<PaymentAuthorizeResponse> authrizedPaymentResponse = 
				pisApiClient.authrizedPayment(paymentAuthorizeResponse.getScaId(), paymentAuthorizeResponse.getAuthorisationId(), 
						resetCookies(cookieStrings), "123456");
		Assert.assertNotNull(authrizedPaymentResponse);
		Assert.assertTrue(authrizedPaymentResponse.getStatusCode().is2xxSuccessful());
		cookieStrings = authrizedPaymentResponse.getHeaders().get("Set-Cookie");
		consentCookieString = readCookie(cookieStrings, "CONSENT");
		Assert.assertNotNull(consentCookieString);
		accessTokenCookieString = readCookie(cookieStrings, "ACCESS_TOKEN");
		Assert.assertNotNull(accessTokenCookieString);

		return authrizedPaymentResponse;
	}

	public ResponseEntity<PaymentAuthorizeResponse> choseScaMethod(ResponseEntity<PaymentAuthorizeResponse> paymentResponse) {
		Assert.assertNotNull(paymentResponse);
		Assert.assertTrue(paymentResponse.getStatusCode().is2xxSuccessful());
		List<String> cookieStrings = paymentResponse.getHeaders().get("Set-Cookie");
		String consentCookieString = readCookie(cookieStrings, "CONSENT");
		Assert.assertNotNull(consentCookieString);
		String accessTokenCookieString = readCookie(cookieStrings, "ACCESS_TOKEN");
		Assert.assertNotNull(accessTokenCookieString);

		PaymentAuthorizeResponse paymentAuthorizeResponse = paymentResponse.getBody();
		ScaUserDataTO scaUserDataTO = paymentAuthorizeResponse.getScaMethods().iterator().next();
		ResponseEntity<PaymentAuthorizeResponse> authrizedPaymentResponse = pisApiClient.selectMethod(paymentAuthorizeResponse.getScaId(), paymentAuthorizeResponse.getAuthorisationId(), 
				scaUserDataTO.getId(), resetCookies(cookieStrings));
		Assert.assertNotNull(authrizedPaymentResponse);
		Assert.assertTrue(authrizedPaymentResponse.getStatusCode().is2xxSuccessful());
		cookieStrings = authrizedPaymentResponse.getHeaders().get("Set-Cookie");
		consentCookieString = readCookie(cookieStrings, "CONSENT");
		Assert.assertNotNull(consentCookieString);
		accessTokenCookieString = readCookie(cookieStrings, "ACCESS_TOKEN");
		Assert.assertNotNull(accessTokenCookieString);

		return authrizedPaymentResponse;
	}
	
}