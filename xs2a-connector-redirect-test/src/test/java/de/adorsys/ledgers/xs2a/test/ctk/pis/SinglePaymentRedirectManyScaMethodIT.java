package de.adorsys.ledgers.xs2a.test.ctk.pis;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import de.adorsys.ledgers.oba.rest.client.ObaPisApiClient;
import de.adorsys.ledgers.xs2a.api.client.PaymentApiClient;
import de.adorsys.ledgers.xs2a.test.ctk.StarterApplication;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = StarterApplication.class)
public class SinglePaymentRedirectManyScaMethodIT {
	private final YAMLMapper ymlMapper = new YAMLMapper();
	private final String paymentService = "payments";
	private final String paymentProduct = "sepa-credit-transfers";

	@Autowired
	private PaymentApiClient paymentApi;
	@Autowired
	private ObaPisApiClient obaPisApiClient;

	private PaymentExecutionHelper paymentInitService;

	@Before
	public void beforeClass() {
		PaymentCase paymentCase = LoadPayment.loadPayment(SinglePaymentRedirectManyScaMethodIT.class,
				SinglePaymentRedirectManyScaMethodIT.class.getSimpleName() + ".yml", ymlMapper);
		paymentInitService = new PaymentExecutionHelper(paymentApi, obaPisApiClient, paymentCase, paymentService, paymentProduct);
	}
//
//	@Test
//	public void test_create_payment() {
//		// Initiate Payment
//		PaymentInitationRequestResponse201 initiatedPayment = paymentInitService.initiatePayment();
//
//		// Login User
//		UpdatePsuAuthenticationResponse psuAuthenticationResponse = paymentInitService.login(initiatedPayment);
//		// TODO: check why not PSUIDENTIFIED
//		checkScaStatus(ScaStatus.PSUAUTHENTICATED, psuAuthenticationResponse);
//		checkTransactionStatusStatus(TransactionStatus.ACCP, psuAuthenticationResponse);
//
//		Assert.assertNull(psuAuthenticationResponse.getChosenScaMethod());
//		Assert.assertNotNull(psuAuthenticationResponse.getScaMethods());
//		Assert.assertEquals(2, psuAuthenticationResponse.getScaMethods().size());
//
//		UpdatePsuAuthenticationResponse choseScaMethodResponse = paymentInitService
//				.choseScaMethod(psuAuthenticationResponse);
//		checkScaStatus(ScaStatus.SCAMETHODSELECTED, choseScaMethodResponse);
//		checkTransactionStatusStatus(TransactionStatus.ACCP, choseScaMethodResponse);
//
//		psuAuthenticationResponse = paymentInitService.authCode(psuAuthenticationResponse);
//		checkScaStatus(ScaStatus.FINALISED, psuAuthenticationResponse);
//		checkTransactionStatusStatus(TransactionStatus.ACSP, psuAuthenticationResponse);
//	}
//
//	private void checkTransactionStatusStatus(TransactionStatus t,
//			UpdatePsuAuthenticationResponse psuAuthenticationResponse) {
//		PaymentInitiationStatusResponse200Json paymentStatus = paymentInitService
//				.loadPaymentStatus(psuAuthenticationResponse);
//		Assert.assertNotNull(paymentStatus);
//		TransactionStatus transactionStatus = paymentStatus.getTransactionStatus();
//		Assert.assertNotNull(transactionStatus);
//		Assert.assertEquals(t, transactionStatus);
//	}
//
//	private void checkScaStatus(ScaStatus s, UpdatePsuAuthenticationResponse psuAuthenticationResponse) {
//		Assert.assertNotNull(psuAuthenticationResponse);
//		ScaStatus scaStatus = psuAuthenticationResponse.getScaStatus();
//		Assert.assertNotNull(scaStatus);
//		Assert.assertEquals(s, scaStatus);
//	}
}