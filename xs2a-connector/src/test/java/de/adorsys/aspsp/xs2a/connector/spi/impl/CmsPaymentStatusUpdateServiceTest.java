package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class
CmsPaymentStatusUpdateServiceTest {
    private static final String PAYMENT_ID = "some payment id";
    private static final String INSTANCE_ID = "UNDEFINED";
    private static final byte[] ASPSP_CONSENT_DATA = "some ASPSP consent Data".getBytes();

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private CmsPsuPisClient cmsPsuPisClient;
    @Mock
    private RequestProviderService requestProviderService;


    @InjectMocks
    private CmsPaymentStatusUpdateService cmsPaymentStatusUpdateService;

    @Test
    void updatePaymentStatus_withIdentifiedAuthorisation_shouldUpdateToAccp() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        GlobalScaResponseTO scaLoginResponse = new GlobalScaResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);
        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.ACCP, INSTANCE_ID);
    }

    @Test
    void updatePaymentStatus_withExemptedAuthorisation_shouldUpdateToAccp() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        GlobalScaResponseTO scaLoginResponse = new GlobalScaResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.EXEMPTED);
        when(consentDataService.response(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);
        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.ACCP, INSTANCE_ID);
    }

    @Test
    void updatePaymentStatus_withAuthenticatedAuthorisation_shouldUpdateToActc() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        GlobalScaResponseTO scaLoginResponse = new GlobalScaResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.PSUAUTHENTICATED);
        when(consentDataService.response(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);
        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.ACTC, INSTANCE_ID);
    }

    @Test
    @Disabled("Due to refactoring SCA")
    void updatePaymentStatus_withOtherAuthorisationStatus_shouldUpdateToRcvd() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        GlobalScaResponseTO scaLoginResponse = new GlobalScaResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.STARTED);
        when(consentDataService.response(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);
        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.RCVD, INSTANCE_ID);
    }

    @Test
    @Disabled("Due to refactoring SCA")
    void updatePaymentStatus_withExceptionOnReadingToken_shouldSkipUpdate() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        when(consentDataService.response(ASPSP_CONSENT_DATA)).thenThrow(IOException.class);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient, never()).updatePaymentStatus(any(), any(), any());
    }
}