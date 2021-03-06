package com.github.mikesafonov.smpp.config;

import com.github.mikesafonov.smpp.core.ClientFactory;
import com.github.mikesafonov.smpp.core.generators.SmppResultGenerator;
import com.github.mikesafonov.smpp.core.reciever.DeliveryReportConsumer;
import com.github.mikesafonov.smpp.core.reciever.ResponseClient;
import com.github.mikesafonov.smpp.core.reciever.ResponseSmppSessionHandler;
import com.github.mikesafonov.smpp.core.sender.SenderClient;
import com.github.mikesafonov.smpp.core.sender.TypeOfAddressParser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.FactoryBean;

import java.util.List;

import static java.util.stream.Collectors.toList;


/**
 * @author Mike Safonov
 */
@RequiredArgsConstructor
public class SmscConnectionFactoryBean implements FactoryBean<SmscConnectionsHolder> {

    private final SmppProperties smppProperties;
    private final SmppResultGenerator smppResultGenerator;
    private final DeliveryReportConsumer deliveryReportConsumer;
    private final TypeOfAddressParser typeOfAddressParser;
    private final ClientFactory clientFactory;

    @Override
    public SmscConnectionsHolder getObject() {
        SmppProperties.Defaults defaults = smppProperties.getDefaults();
        List<SmscConnection> connections = smppProperties.getConnections().entrySet().stream()
                .map(smsc -> getSmscConnection(defaults, smsc.getKey(), smsc.getValue()))
                .collect(toList());
        return new SmscConnectionsHolder(connections);
    }

    private SmscConnection getSmscConnection(SmppProperties.Defaults defaults, String name, SmppProperties.SMSC smsc) {
        ConnectionMode connectionMode = (smsc.getConnectionMode() == null) ? defaults.getConnectionMode() : smsc.getConnectionMode();
        switch (connectionMode) {
            case MOCK: {
                return new SmscConnection(name, clientFactory.mockSender(name, smppResultGenerator));
            }
            case TEST: {
                return getTestSmscConnection(defaults, name, smsc);
            }
            case STANDARD: {
                return getStandardSmscConnection(defaults, name, smsc);
            }
            default: {
                throw new RuntimeException("Unknown connection mode " + connectionMode);
            }
        }
    }

    private SmscConnection getTestSmscConnection(SmppProperties.Defaults defaults, String name, SmppProperties.SMSC smsc) {
        SenderClient standardSender = clientFactory.standardSender(name, defaults, smsc, typeOfAddressParser);
        SenderClient testSenderClient = clientFactory.testSender(standardSender, defaults, smsc, smppResultGenerator);
        ResponseClient responseClient = clientFactory.standardResponse(name, defaults, smsc);
        setupClients(testSenderClient, responseClient);
        return new SmscConnection(name, responseClient, testSenderClient);
    }

    private SmscConnection getStandardSmscConnection(SmppProperties.Defaults defaults, String name, SmppProperties.SMSC smsc) {
        SenderClient senderClient = clientFactory.standardSender(name, defaults, smsc, typeOfAddressParser);
        ResponseClient responseClient = clientFactory.standardResponse(name, defaults, smsc);
        setupClients(senderClient, responseClient);
        return new SmscConnection(name, responseClient, senderClient);
    }

    private void setupClients(SenderClient senderClient, ResponseClient responseClient) {
        if (smppProperties.isSetupRightAway()) {
            senderClient.setup();
            ResponseSmppSessionHandler responseSmppSessionHandler = new ResponseSmppSessionHandler(responseClient, deliveryReportConsumer);
            responseClient.setup(responseSmppSessionHandler);
        }
    }

    @Override
    public Class<?> getObjectType() {
        return SmscConnectionsHolder.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
