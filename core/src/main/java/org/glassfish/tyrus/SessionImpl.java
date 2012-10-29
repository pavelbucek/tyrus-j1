/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus;


import javax.net.websocket.ClientContainer;
import javax.net.websocket.CloseReason;
import javax.net.websocket.Encoder;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.Session;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the WebSocketConversation.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class SessionImpl<T> implements Session<T> {

    private final ClientContainer container;
    private final EndpointWrapper endpoint;
    private final RemoteEndpointWrapper remote;
    private final String negotiatedSubprotocol;
    private final List<String> negotiatedExtensions;
    private final boolean isSecure;
    private final URI uri;
    private final String queryString;
    private final Map<String, String> pathParameters;
    private Map<MessageHandler, MessageHandler> messageHandlerToInvokableMessageHandlers = new HashMap<MessageHandler, MessageHandler>();
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private long timeout;
    private long maximumMessageSize = 8192;

    /**
     * Timestamp of the last send/receive message activity.
     */
    private long lastConnectionActivity;

    SessionImpl(ClientContainer container, RemoteEndpoint remoteEndpoint, EndpointWrapper endpointWrapper,
                String subprotocol, List<String> extensions, boolean isSecure,
                URI uri, String queryString, Map<String, String> pathParameters) {
        this.container = container;
        this.endpoint = endpointWrapper;
        this.negotiatedSubprotocol = subprotocol;
        this.negotiatedExtensions = extensions == null ? Collections.<String>emptyList() :
                Collections.unmodifiableList(extensions);
        this.isSecure = isSecure;
        this.uri = uri;
        this.queryString = queryString;
        this.pathParameters = Collections.unmodifiableMap(pathParameters);
        this.remote = new RemoteEndpointWrapper(this, remoteEndpoint, endpointWrapper);
    }

    /**
     * Web Socket protocol version used.
     *
     * @return protocol version
     */
    @Override
    public String getProtocolVersion() {
        return "13";
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return negotiatedSubprotocol;
    }

    @Override
    public RemoteEndpoint getRemote() {
        return remote;
    }

    // TODO: should be removed from the API - does not provide much value
    // TODO: also the <T> from RemoteEndpoint should be removed
    @Override
    public RemoteEndpoint<T> getRemoteL(Class<T> aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isActive() {
        return endpoint.isActive(this);
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void close() throws IOException {
        this.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "no reason given"));
    }

    /**
     * Closes the underlying connection this session is based upon.
     */
    @Override
    public void close(CloseReason closeReason) throws IOException {
        remote.close(closeReason);
    }

    @Override
    public String toString() {
        return "Session(" + hashCode() + ", " + this.isActive() + ")";
    }

    public void setTimeout(long seconds) {
        this.timeout = seconds;

    }

    @Override
    public void setMaximumMessageSize(long maximumMessageSize) {
        this.maximumMessageSize = maximumMessageSize;
    }

    @Override
    public long getMaximumMessageSize() {
        return this.maximumMessageSize;
    }

    @Override
    public List<String> getNegotiatedExtensions() {
        return this.negotiatedExtensions;
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public long getInactiveTime() {
        return (System.currentTimeMillis() - this.lastConnectionActivity) / 1000;
    }

    @Override
    public ClientContainer getContainer() {
        return this.container;
    }

    @Override
    public void setEncoders(List<Encoder> encoders) {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMessageHandler(MessageHandler listener) {
        MessageHandler invokable;
        if (listener instanceof MessageHandler.CharacterStream) {
            invokable = new AsyncTextToCharStreamAdapter((MessageHandler.CharacterStream) listener);
        } else if (listener instanceof MessageHandler.BinaryStream) {
            invokable = new AsyncBinaryToOutputStreamAdapter((MessageHandler.BinaryStream) listener);
        } else {
            invokable = listener;
        }
        this.messageHandlerToInvokableMessageHandlers.put(listener, invokable);
    }


    @Override
    public Set<MessageHandler> getMessageHandlers() {
        return Collections.unmodifiableSet(this.messageHandlerToInvokableMessageHandlers.keySet());
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        this.messageHandlerToInvokableMessageHandlers.remove(listener);
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    // TODO: this method should be deleted?
    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.emptyMap();
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    void updateLastConnectionActivity() {
        this.lastConnectionActivity = System.currentTimeMillis();
    }

    void notifyMessageHandlers(String message, List<DecoderWrapper> availableDecoders) {
        updateLastConnectionActivity();

        boolean decoded = false;

        if (availableDecoders.isEmpty()) {
            System.out.println("No Decoder found");
        }

        try {
            for (DecoderWrapper decoder : availableDecoders) {
                for (MessageHandler mh : this.getOrderedMessageHandlers()) {
                    if (mh instanceof MessageHandler.Text) {
                        ((MessageHandler.Text) mh).onMessage(message);
                        decoded = true;
                        break;

                    } else if (mh instanceof DecodedObjectMessageHandler) {
                        DecodedObjectMessageHandler domh = (DecodedObjectMessageHandler) mh;
                        Class<?> type = domh.getType();
                        if (type != null && type.isAssignableFrom(decoder.getType())) {
                            Object object = endpoint.decodeCompleteMessage(message, domh.getType(), true);
                            if (object != null) {
                                domh.onMessage(object);
                                decoded = true;
                                break;
                            }
                        }
                    } else if (mh instanceof MessageHandler.DecodedObject) {
                        Class<?> type = this.getClassType(mh.getClass(), MessageHandler.DecodedObject.class);
                        if (type != null && type.isAssignableFrom(decoder.getType())) {
                            Object object = endpoint.decodeCompleteMessage(message, type, true);
                            if (object != null) {
                                ((MessageHandler.DecodedObject) mh).onMessage(object);
                                decoded = true;
                                break;
                            }
                        }
                    }
                }
                if (decoded) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void notifyMessageHandlers(ByteBuffer message, List<DecoderWrapper> availableDecoders) {
        updateLastConnectionActivity();

        boolean decoded = false;

        if (availableDecoders.isEmpty()) {
            System.out.println("No Decoder found");
        }

        try {
            for (DecoderWrapper decoder : availableDecoders) {
                for (MessageHandler mh : this.getOrderedMessageHandlers()) {
                    if (mh instanceof MessageHandler.Binary) {
                        ((MessageHandler.Binary) mh).onMessage(message);
                        decoded = true;
                        break;

                    } else if (mh instanceof DecodedObjectMessageHandler) {
                        DecodedObjectMessageHandler domh = (DecodedObjectMessageHandler) mh;
                        Class<?> type = domh.getType();
                        if (type != null && type.isAssignableFrom(decoder.getType())) {
                            Object object = endpoint.decodeCompleteMessage(message, domh.getType(), true);
                            if (object != null) {
                                domh.onMessage(object);
                                decoded = true;
                                break;
                            }
                        }
                    } else if (mh instanceof MessageHandler.DecodedObject) {
                        Class<?> type = this.getClassType(mh.getClass(), MessageHandler.DecodedObject.class);
                        if (type != null && type.isAssignableFrom(decoder.getType())) {
                            Object object = endpoint.decodeCompleteMessage(message, type, true);
                            if (object != null) {
                                ((MessageHandler.DecodedObject) mh).onMessage(object);
                                decoded = true;
                                break;
                            }
                        }
                    }
                }
                if (decoded) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void notifyMessageHandlers(ByteBuffer partialBytes, boolean last) {
        boolean handled = false;

        for (MessageHandler handler : this.getInvokableMessageHandlers()) {
            if (handler instanceof MessageHandler.AsyncBinary) {
                ((MessageHandler.AsyncBinary) handler).onMessagePart(partialBytes, last);
                handled = true;
                break;
            }
        }

        if (!handled) {
            System.out.println("Unhandled partial binary message in EndpointWrapper");
        }
    }

    /*
    * Initial implementation policy:
    * - if there is a streaming message handler invoke it
    * - if there is a blocking handler, use an adapter to invoke it
    */
    void notifyMessageHandlers(String partialString, boolean last) {
        boolean handled = false;

        for (MessageHandler handler : this.getInvokableMessageHandlers()) {
            MessageHandler.AsyncText baseHandler;
            if (handler instanceof MessageHandler.AsyncText) {
                baseHandler = (MessageHandler.AsyncText) handler;
                baseHandler.onMessagePart(partialString, last);
                handled = true;
                break;
            }
        }

        if (!handled) {
            System.out.println("Unhandled text message in EndpointWrapper");
        }
    }

    private Class<?> getClassType(Class<?> inspectedClass, Class<?> rootClass) {
        ReflectionHelper.DeclaringClassInterfacePair p = ReflectionHelper.getClass(inspectedClass, rootClass);
        Class[] as = ReflectionHelper.getParameterizedClassArguments(p);
        if (as == null) {
            return null;
        } else {
            return as[0];
        }
    }

    Set<MessageHandler> getInvokableMessageHandlers() {
        Set s = new HashSet();
        for (MessageHandler mh : this.messageHandlerToInvokableMessageHandlers.values()) {
            s.add(mh);
        }
        return s;
    }

    private List<MessageHandler> getOrderedMessageHandlers() {
        Set<MessageHandler> handlers = this.getMessageHandlers();
        ArrayList<MessageHandler> result = new ArrayList<MessageHandler>();

        result.addAll(handlers);

        Collections.sort(result, new MessageHandlerComparator());

        return result;
    }

    private class MessageHandlerComparator implements Comparator<MessageHandler> {

        @Override
        public int compare(MessageHandler o1, MessageHandler o2) {
            if (o1 instanceof MessageHandler.DecodedObject) {
                if (o2 instanceof MessageHandler.DecodedObject) {
                    Class<?> type1 = getClassType(o1.getClass(), MessageHandler.DecodedObject.class);
                    Class<?> type2 = getClassType(o2.getClass(), MessageHandler.DecodedObject.class);

                    if (type1.isAssignableFrom(type2)) {
                        return 1;
                    } else if (type2.isAssignableFrom(type1)) {
                        return -1;
                    } else {
                        return 0;
                    }
                } else {
                    return 1;
                }
            } else if (o2 instanceof MessageHandler.DecodedObject) {
                return 1;
            }
            return 0;
        }
    }
}