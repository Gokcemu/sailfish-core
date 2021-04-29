/******************************************************************************
 * Copyright 2009-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.sf.services.tcpip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.aml.script.actions.WaitAction;
import com.exactpro.sf.common.codecs.AbstractCodec;
import com.exactpro.sf.common.codecs.CodecFactory;
import com.exactpro.sf.common.messages.AttributeNotFoundException;
import com.exactpro.sf.common.messages.IMessage;
import com.exactpro.sf.common.messages.IMessageFactory;
import com.exactpro.sf.common.messages.MessageNotFoundException;
import com.exactpro.sf.common.messages.MsgMetaData;
import com.exactpro.sf.common.messages.structures.IDictionaryStructure;
import com.exactpro.sf.common.services.ServiceInfo;
import com.exactpro.sf.common.services.ServiceName;
import com.exactpro.sf.common.util.EPSCommonException;
import com.exactpro.sf.configuration.IDictionaryManager;
import com.exactpro.sf.configuration.ILoggingConfigurator;
import com.exactpro.sf.configuration.suri.SailfishURI;
import com.exactpro.sf.messages.IncomingMessageFactory;
import com.exactpro.sf.scriptrunner.actionmanager.actioncontext.IActionContext;
import com.exactpro.sf.services.IAcceptorService;
import com.exactpro.sf.services.IInitiatorService;
import com.exactpro.sf.services.IServiceContext;
import com.exactpro.sf.services.IServiceHandler;
import com.exactpro.sf.services.IServiceMonitor;
import com.exactpro.sf.services.IServiceSettings;
import com.exactpro.sf.services.ISession;
import com.exactpro.sf.services.ITaskExecutor;
import com.exactpro.sf.services.MessageHelper;
import com.exactpro.sf.services.ServiceException;
import com.exactpro.sf.services.ServiceHandlerRoute;
import com.exactpro.sf.services.ServiceStatus;
import com.exactpro.sf.services.WrapperNioSocketAcceptor;
import com.exactpro.sf.services.mina.MINASession;
import com.exactpro.sf.services.mina.MINAUtil;
import com.exactpro.sf.services.util.ServiceUtil;
import com.exactpro.sf.storage.IMessageStorage;

public class TCPIPServer extends IoHandlerAdapter implements IAcceptorService, IInitiatorService, ITCPIPService {

    private final Logger logger = LoggerFactory
            .getLogger(ILoggingConfigurator.getLoggerName(this));

    private volatile ServiceStatus curStatus;

    private IServiceContext serviceContext;
    private TCPIPServerSettings settings;
    private IServiceHandler handler;
    private MessageHelper messageHelper;
    private final List<ISession> sessions;
    private TCPIPServerSession session;
    private WrapperNioSocketAcceptor acceptor;
    private ServiceName serviceName;
    private ITaskExecutor taskExecutor;
    private Class<? extends AbstractCodec> codecClass;
    private IServiceMonitor monitor;
    private ILoggingConfigurator logConfigurator;
    protected IDictionaryStructure dictionary;
    private IFieldConverter fieldConverter;
    private ServiceInfo serviceInfo;
    private IMessageStorage storage;
    final Map<IoSession, MINASession> sessionMap = new ConcurrentHashMap<>();
    protected IMessageFactory messageFactory;
    private InetSocketAddress adress;

    public TCPIPServer() {
        curStatus = ServiceStatus.CREATING;
        this.codecClass = null;
        this.sessions = new ArrayList<>();
        this.curStatus = ServiceStatus.CREATED;
    }

    @Override
    public void init(IServiceContext serviceContext, IServiceMonitor serviceMonitor,
            IServiceHandler handler, IServiceSettings settings, ServiceName serviceName) {
        try {
            changeStatus(ServiceStatus.INITIALIZING, "Service initializing", null);

            this.serviceName = Objects.requireNonNull(serviceName, "'Service name' parameter");

            this.serviceContext = Objects.requireNonNull(serviceContext, "'Service context' parameter");

            this.monitor = Objects.requireNonNull(serviceMonitor, "'Service monitor' parameter");

            this.logConfigurator = Objects.requireNonNull(this.serviceContext.getLoggingConfigurator(), "'Logging configurator' parameter");

            this.taskExecutor = this.serviceContext.getTaskExecutor();
            if (settings == null) {
                throw new NullPointerException("'settings' parameter");
            }
            this.settings = (TCPIPServerSettings)settings;

            this.handler = Objects.requireNonNull(handler, "'Service handler' parameter");

            this.storage = Objects.requireNonNull(this.serviceContext.getMessageStorage(), "'Message storage' parameter");

            this.serviceInfo = Objects.requireNonNull(serviceContext.lookupService(getServiceName()), "serviceInfo cannot be null");

            IMessageFactory defaultFactory = null;
            SailfishURI dictionaryName = this.settings.getDictionaryName();
            IDictionaryManager dictionaryManager = this.serviceContext.getDictionaryManager();
            this.messageHelper = new TCPIPMessageHelper(this.settings.isDepersonalizationIncomingMessages());

            if (dictionaryName != null) {
                this.dictionary = dictionaryManager.getDictionary(dictionaryName);

                if (StringUtils.isNotEmpty(this.settings.getFieldConverterClassName())) {
                    try {
                        Class<?> fieldConverterClass = getClass().getClassLoader()
                                .loadClass(this.settings.getFieldConverterClassName());
                        fieldConverter = (IFieldConverter) fieldConverterClass.newInstance();
                        fieldConverter.init(dictionary, dictionary.getNamespace());
                    } catch (Exception e) {
                        throw new IllegalStateException("fieldConverterClass: " + e.getMessage(), e);
                    }
                }

                defaultFactory = dictionaryManager.getMessageFactory(dictionaryName);
                messageHelper.init(defaultFactory, dictionary);
            } else {
                defaultFactory = dictionaryManager.createMessageFactory();
            }

            if(fieldConverter == null) {
                this.fieldConverter = new DefaultFieldConverter();
            }
            try {
                this.codecClass = getClass().getClassLoader().loadClass(this.settings.getCodecClassName()).asSubclass(AbstractCodec.class);
            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
                changeStatus(ServiceStatus.ERROR, "ERROR while initializing service", null);
                throw new ServiceException("Could not find codec class [" + this.settings.getCodecClassName() + "]", e);
            }


            this.messageFactory = this.settings.isDepersonalizationIncomingMessages() ?
                    new IncomingMessageFactory(defaultFactory) : defaultFactory;

            if (this.settings.isUseSSL()) {
                if (this.settings.getSslKeyStore() != null || this.settings.getSslKeyStorePassword() != null
                        || this.settings.getSslProtocol() != null || this.settings.getKeyStoreType() != null) {
                    String format = "UseSSL is enabled, but requred parameter %s are missing";
                    if (this.settings.getSslKeyStore() == null) {
                        throw new EPSCommonException(String.format(format, "SslKeyStore"));
                    } else if (this.settings.getSslKeyStorePassword() == null) {
                        throw new EPSCommonException(String.format(format, "SslKeyStorePassword"));
                    } else if (this.settings.getSslProtocol() == null) {
                        throw new EPSCommonException(String.format(format, "SslProtocol"));
                    } else if (this.settings.getKeyStoreType() == null) {
                        throw new EPSCommonException(String.format(format, "KeyStoreType"));
                    }
                }
            }

            changeStatus(ServiceStatus.INITIALIZED, "Service initialized", null);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            changeStatus(ServiceStatus.ERROR, "Init error : " + e.getMessage(), e);
            throw new EPSCommonException(e);
        }
        logger.info("initialization finished");

    }

    private void installSSLFilter() throws NoSuchAlgorithmException, KeyStoreException,
            CertificateException, IOException, KeyManagementException, UnrecoverableKeyException {

        acceptor.getFilterChain().addFirst("SSLFilter",
                MINAUtil.createSslFilter(false, settings.getSslProtocol(),
                        settings.getKeyStoreType(), settings.getSslKeyStore(),
                        settings.getSslKeyStorePassword().toCharArray()));
    }

    @Override
    public void start() {
        logConfigurator.createAndRegister(getServiceName(), this);
        try {
            changeStatus(ServiceStatus.STARTING, "service starting", null);

            this.acceptor = new WrapperNioSocketAcceptor(taskExecutor);

            CodecFactory codecFactory = new CodecFactory(serviceContext, messageFactory, dictionary, codecClass, settings.createCodecSettings());

            if (settings.isUseSSL()) {
                installSSLFilter();
            }

            acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
            acceptor.setHandler(this);
            acceptor.setReuseAddress(true);
            adress = new InetSocketAddress(settings.getHost(), settings.getPort());
            acceptor.bind(adress);

            this.session = new TCPIPServerSession(this);
            new Thread(session).start();

            changeStatus(ServiceStatus.STARTED, "service started", null);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            changeStatus(ServiceStatus.ERROR, e.getMessage(), e);
        }

    }

    @Override
    public void dispose() {
        changeStatus(ServiceStatus.DISPOSING, "Service disposing", null);

        try {
            if(acceptor != null) {
                acceptor.unbind(adress);
                acceptor.dispose();
                this.acceptor = null;
            }
            if(session != null) {
                session.close();
                this.session = null;
            }
        } catch (Exception err) {
            changeStatus(ServiceStatus.ERROR, "Error when disposing", null);
        }

        changeStatus(ServiceStatus.DISPOSED, "Service disposed", null);

        if (logConfigurator != null) {
            logConfigurator.destroyAppender(getServiceName());
        }

    }

    @Override
    public IServiceHandler getServiceHandler() {
        return handler;
    }

    @Override
    public void setServiceHandler(IServiceHandler handler) {
        this.handler = handler;

    }

    @Override
    public String getName() {
        return serviceName.toString();
    }

    @Override
    public ServiceName getServiceName() {
        return serviceName;
    }

    @Override
    public ServiceStatus getStatus() {
        return curStatus;
    }

    @Override
    public IServiceSettings getSettings() {
        return settings;
    }

    @Override
    public List<ISession> getSessions() {
        return sessions;
    }

    @Override
    public void sessionCreated(IoSession session) throws Exception {
        TCPIPSession tcpipSession = new TCPIPSession(serviceName, session, getSettings().getSendMessageTimeout());
        logConfigurator.registerLogger(tcpipSession, getServiceName());
        sessions.add(tcpipSession);
        sessionMap.put(session, tcpipSession);

        storeSessionEventMessage(session, "connection created!");

        logger.info("sessionCreated: {} {} {}",
                session, session.getClass().getCanonicalName(), session.hashCode());
        session.setReceiveLimit(settings.getReceiveLimit());
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        handler.sessionOpened(sessionMap.get(session));

        storeSessionEventMessage(session, "connection opened!");

        logger.info("sessionOpened: {} {} {}",
                session, session.getClass().getCanonicalName(), session.hashCode());
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        ISession iSession = sessionMap.remove(session);
        sessions.remove(iSession);

        storeSessionEventMessage(session, "connection closed!");

        logger.info("sessionClosed: {} {} {}",
                session, session.getClass().getCanonicalName(), session.hashCode());
    }

    private void storeSessionEventMessage(IoSession session, String eventMessage) {
        String from = serviceName.toString();
        String to = getRemoteAddress(session);
        IMessage iMessage = ServiceUtil.createServiceMessage(eventMessage, from, to, serviceInfo, messageFactory);
        storage.storeMessage(iMessage);
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        logger.debug("Message received:{}", message);

        if (message instanceof IMessage) {
            IMessage iMessage = (IMessage) message;
            MsgMetaData metaData = iMessage.getMetaData();
            metaData.setToService(serviceName.toString());
            metaData.setFromService(getRemoteAddress(session));
            metaData.setServiceInfo(serviceInfo);

            storage.storeMessage(iMessage);

            logger.debug("Message stored:{}", message);

            if (metaData.isAdmin()) {
                logger.debug("Add fromAdmin: {}", iMessage.getName());
                handler.putMessage(this.session, ServiceHandlerRoute.FROM_ADMIN, iMessage);
            } else {
                logger.debug("Add fromApp: {}", iMessage.getName());
                handler.putMessage(this.session, ServiceHandlerRoute.FROM_APP, iMessage);
            }
        }
    }

    @Override
    public void messageSent(IoSession session, Object message) throws Exception {
        logger.debug("Message sent:{}", message);

        if (message instanceof IMessage) {
            IMessage iMessage = (IMessage) message;
            MsgMetaData metaData = iMessage.getMetaData();
            metaData.setToService(getRemoteAddress(session));
            metaData.setFromService(serviceName.toString());
            metaData.setServiceInfo(serviceInfo);

            storage.storeMessage(iMessage);

            logger.debug("Message stored:{}", message);

            if (metaData.isAdmin()) {
                logger.info("Add toAdmin: {}", iMessage.getName());
                handler.putMessage(this.session, ServiceHandlerRoute.TO_ADMIN, iMessage);
            } else {
                logger.info("Add toApp: {}", iMessage.getName());
                handler.putMessage(this.session, ServiceHandlerRoute.TO_APP, iMessage);
            }
        }
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        if (cause instanceof MessageParseException) {
            MessageParseException e = (MessageParseException) cause;
            byte[] rawMessage = e.getRawMessage().getBytes();
            IMessage msg = messageFactory.createMessage(TCPIPMessageHelper.REJECTED_MESSAGE_NAME_AND_NAMESPACE,
                    TCPIPMessageHelper.REJECTED_MESSAGE_NAME_AND_NAMESPACE);
            MsgMetaData metaData = msg.getMetaData();
            metaData.setFromService(serviceName.toString());
            metaData.setToService(settings.getHost() + ":" + settings.getPort());
            metaData.setRawMessage(rawMessage);
            metaData.setServiceInfo(serviceInfo);

            storage.storeMessage(msg);
        }
        logger.error("Have error: {}", cause, cause);
    }

    protected void changeStatus(ServiceStatus status, String message, Throwable e) {
        this.curStatus = status;
        ServiceUtil.changeStatus(this, monitor, status, message, e);
    }

    @Override
    public ISession getSession() {
        return session;
    }

    @Override
    public void connect() throws Exception {
    }

    @Override
    public IMessage receive(IActionContext actionContext, IMessage msg) throws InterruptedException {
        IMessage convertedMessage = fieldConverter.convertFields(msg, messageFactory, true);
        try {
            return WaitAction.waitForMessage(actionContext, convertedMessage, !messageHelper.isAdmin(convertedMessage));
        } catch (MessageNotFoundException e) {
            logger.error(e.getMessage(), e);
            throw new ServiceException("Unknown message", e);
        } catch (AttributeNotFoundException e) {
            logger.error(e.getMessage(), e);
            throw new ServiceException("Incorrect dictionary", e);
        }
    }

    public Map<IoSession, MINASession> getSessionMap() {
        return sessionMap;
    }

    private String getRemoteAddress(IoSession session) {
        return Objects.toString(session.getRemoteAddress(), "UNKNOWN");
    }

    @Override
    public boolean sendMessage(IMessage message, long timeOut) throws InterruptedException {

        if(session == null) {
            return false;
        }

        MsgMetaData metaData = message.getMetaData();
        byte[] data = metaData.getRawMessage();
        IoBuffer buffer = IoBuffer.wrap(data);

        session.send(buffer, timeOut);

        metaData.setAdmin(false);
        metaData.setFromService(getName());
        metaData.setToService(session.toString());
        metaData.setServiceInfo(serviceInfo);

        storage.storeMessage(message);

        return true;
    }

    @Override
    public boolean isConnected() {
        return !sessionMap.isEmpty();
    }
}
