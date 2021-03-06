package no.difi.sdp;

import no.difi.kontaktinfo.wsdl.oppslagstjeneste_14_05.Oppslagstjeneste1405;
import no.difi.kontaktregister.external.client.cxf.WSS4JInterceptorHelper;
import no.difi.sdp.client.KlientKonfigurasjon;
import no.difi.sdp.client.SikkerDigitalPostKlient;
import no.difi.sdp.client.domain.Noekkelpar;
import no.difi.sdp.client.domain.TekniskAvsender;
import no.difi.sdp.digitalpost.DigitalPostProdusent;
import no.difi.sdp.digitalpost.Forsendelseskilde;
import no.difi.sdp.send.HentKvittering;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;

import java.io.InputStream;
import java.security.KeyStore;

import static org.apache.commons.lang3.Validate.notNull;

public class SDPService {

    private static final String MELDINGSFORMIDLER_URI = "https://qaoffentlig.meldingsformidler.digipost.no/api/ebms";
    private static final String AVSENDER_ORGNUMMER = "991825827";
    private static final String KEYSTORE_RESOURCE_NAME = "/keystore.jce";

    private final SikkerDigitalPostKlient klient;
    private final Forsendelseskilde forsendelseskilde;
    private final Oppslagstjeneste1405 oppslagstjeneste;
    private final SDPStatus sdpStatus;
    private final Thread kvitteringThread;

    private DigitalPostProdusent digitalPostProdusent;

    public SDPService() {
        Noekkelpar noekkelpar;
        InputStream keystoreResource = notNull(this.getClass().getResourceAsStream(KEYSTORE_RESOURCE_NAME),
        		"The keystore file " + KEYSTORE_RESOURCE_NAME + " does not exist. How to create a keystore is described here: https://github.com/difi/sikker-digital-post-java-klient#sertifikater");
        try {
            KeyStore keyStore = KeyStore.getInstance("JCEKS");

            // Last keystore som inneholder sertifikatkjede og privatnøkkel for avsender, samt eventuelt trust store for rotsertifikat brukt av meldingsformidler.
			keyStore.load(keystoreResource, "abcd1234".toCharArray());
            noekkelpar = Noekkelpar.fraKeyStore(keyStore, "meldingsformidler", "abcd1234");
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to init keystore", e);
        }

        klient = new SikkerDigitalPostKlient(TekniskAvsender.builder(AVSENDER_ORGNUMMER, noekkelpar).build(), KlientKonfigurasjon.builder().meldingsformidlerRoot(MELDINGSFORMIDLER_URI).build());

        oppslagstjeneste = hentOppslagstjeneste();
        forsendelseskilde = new Forsendelseskilde(oppslagstjeneste);
        sdpStatus = new SDPStatus();
        digitalPostProdusent = new DigitalPostProdusent(forsendelseskilde, klient, sdpStatus);

        // Alltid lytt på kvitteringer
        kvitteringThread = new Thread(new HentKvittering(klient, sdpStatus), "ReceiptPollingThread");
        kvitteringThread.start();
    }

    public void startSending(Integer sendIntervalMs) {
        digitalPostProdusent.setSendInterval(sendIntervalMs);

        if (!digitalPostProdusent.isRunning()) {
            new Thread(digitalPostProdusent, "LetterProducer").start();
        }
    }

    public void stopSending() {
        digitalPostProdusent.stop();
    }

    public void pullReceipt() {
        kvitteringThread.interrupt();
    }

    public String getStatus() {
        return this.sdpStatus.getStatusString();
    }

    public String getQueueStatus() {
        return this.sdpStatus.getQueueStatusString();
    }

    private Oppslagstjeneste1405 hentOppslagstjeneste() {
        String serviceAddress = System.getProperty("kontaktinfo.address.location");
        if(serviceAddress == null) {
            serviceAddress = "https://kontaktinfo-ws-ver2.difi.no/kontaktinfo-external/ws-v3";
        }

        // Enables running against alternative endpoints to the one specified in the WSDL
        JaxWsProxyFactoryBean jaxWsProxyFactoryBean = new JaxWsProxyFactoryBean();
        jaxWsProxyFactoryBean.setServiceClass(Oppslagstjeneste1405.class);
        jaxWsProxyFactoryBean.setAddress(serviceAddress);

        // Configures WS-Security
        WSS4JInterceptorHelper.addWSS4JInterceptors(jaxWsProxyFactoryBean);
        return (Oppslagstjeneste1405) jaxWsProxyFactoryBean.create();
    }
}
