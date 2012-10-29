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

import javax.net.websocket.CloseReason;
import javax.net.websocket.EncodeException;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.SendHandler;
import javax.net.websocket.SendResult;
import javax.net.websocket.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * Wrapps the {@link RemoteEndpoint} and represents the other side of the websocket connection.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public final class RemoteEndpointWrapper<T> implements RemoteEndpoint<T> {

    private final RemoteEndpoint remoteEndpoint;
    private final SessionImpl session;
    private final EndpointWrapper endpointWrapper;

    RemoteEndpointWrapper(SessionImpl session, RemoteEndpoint remoteEndpoint, EndpointWrapper endpointWrapper) {
        this.remoteEndpoint = remoteEndpoint;
        this.endpointWrapper = endpointWrapper;
        this.session = session;
    }

    @Override
    public void sendString(String data) throws IOException {
        this.remoteEndpoint.sendString(data);
        this.session.updateLastConnectionActivity();
    }

    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        this.remoteEndpoint.sendBytes(data);
        this.session.updateLastConnectionActivity();
    }

    @Override
    public void sendPartialString(String fragment, boolean isLast) throws IOException {
        this.remoteEndpoint.sendPartialString(fragment, isLast);
        this.session.updateLastConnectionActivity();
    }


    @Override
    public void sendPartialBytes(ByteBuffer byteBuffer, boolean isLast) throws IOException {
        this.remoteEndpoint.sendPartialBytes(byteBuffer, isLast);
        this.session.updateLastConnectionActivity();
    }

    @Override
    public OutputStream getSendStream() throws IOException {
        return new OutputStreamToAsyncBinaryAdapter(this);
    }

    @Override
    public Writer getSendWriter() throws IOException {
        return new WriterToAsyncTextAdapter(this);
    }

    @Override
    public void sendObject(T o) throws IOException, EncodeException {
        this.session.updateLastConnectionActivity();
        sendPolymorphic(o);
    }

    @Override
    public Future<SendResult> sendString(String text, SendHandler completion) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.TEXT);
        Future<SendResult> fsr = goesAway.send(text, completion);
        this.session.updateLastConnectionActivity();
        return fsr;
    }

    @Override
    public Future<SendResult> sendBytes(ByteBuffer data, SendHandler completion) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.BINARY);
        Future<SendResult> fsr = goesAway.send(data, completion);
        this.session.updateLastConnectionActivity();
        return fsr;
    }

    @Override
    public Future<SendResult> sendObject(T o, SendHandler completion) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.OBJECT);
        Future<SendResult> fsr = goesAway.send(o, completion);
        this.session.updateLastConnectionActivity();
        return fsr;
    }

    @Override
    public void sendPing(ByteBuffer applicationData) {
        this.remoteEndpoint.sendPing(applicationData);
        this.session.updateLastConnectionActivity();
    }

    @Override
    public void sendPong(ByteBuffer applicationData) {
        this.remoteEndpoint.sendPong(applicationData);
        this.session.updateLastConnectionActivity();
    }

    @Override
    public String toString() {
        return "Wrapped: " + getClass().getSimpleName();
    }

    private void sendPrimitiveMessage(Object data) throws IOException, EncodeException {
        if (isPrimitiveData(data)) {
            this.sendString(data.toString());
        } else {
            throw new EncodeException("object " + data + " is not a primitive type.", data);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendPolymorphic(Object o) throws IOException, EncodeException {
        if (o instanceof String) {
            this.sendString((String) o);
        } else if (isPrimitiveData(o)) {
            this.sendPrimitiveMessage(o);
        } else {
            Object toSend = endpointWrapper.doEncode(o);
            if(toSend instanceof String){
                this.sendString((String)toSend);
            } else if(toSend instanceof ByteBuffer){
                this.sendBytes((ByteBuffer) toSend);
            } else if(toSend instanceof StringWriter){
                StringWriter writer = (StringWriter) toSend;
                StringBuffer sb = writer.getBuffer();
                this.sendString(sb.toString());
            } else if(toSend instanceof ByteArrayOutputStream){
                ByteArrayOutputStream baos = (ByteArrayOutputStream) toSend;
                ByteBuffer buffer = ByteBuffer.wrap(baos.toByteArray());
                this.sendBytes(buffer);
            }

        }
    }

    private boolean isPrimitiveData(Object data) {
        Class dataClass = data.getClass();
        return (dataClass.equals(Integer.class) ||
                dataClass.equals(Byte.class) ||
                dataClass.equals(Short.class) ||
                dataClass.equals(Long.class) ||
                dataClass.equals(Float.class) ||
                dataClass.equals(Double.class) ||
                dataClass.equals(Boolean.class) ||
                dataClass.equals(Character.class));
    }

    public void close(CloseReason cr) throws IOException {
        System.out.println("Close  public void close(CloseReason cr): " + cr);
        // TODO: implement
//        this.remoteEndpoint.close(1000, null);
    }

    public Session getSession() {
        return session;
    }

    public void updateLastConnectionActivity() {
        session.updateLastConnectionActivity();
    }
}