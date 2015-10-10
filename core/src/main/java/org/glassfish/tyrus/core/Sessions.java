/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.core;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.websocket.Session;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Sessions {

    private final Set<TyrusEndpointWrapper> endpointWrappers;

    public Sessions(Set<TyrusEndpointWrapper> endpointWrappers) {
        this.endpointWrappers = endpointWrappers;
    }

    /**
     * Return a copy of the Set of all the open web socket sessions that represent
     * connections to the same endpoint to which this session represents a connection.
     * The Set includes the session this method is called on. These
     * sessions may not still be open at any point after the return of this method. For
     * example, iterating over the set at a later time may yield one or more closed sessions. Developers
     * should use session.isOpen() to check.
     *
     * @return the set of sessions, open at the time of return.
     */
    public List<Session> getOpenSessions(String path) {

        final TyrusEndpointWrapper wrapper = lookupTyrusEndpointWrapper(path);
        if (wrapper != null) {
            return wrapper.getOpenSessions().stream().<Session>map(new Function<TyrusSession, Session>() {
                @Override
                public Session apply(TyrusSession tyrusSession) {
                    return tyrusSession;
                }
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Broadcasts text message to all connected clients.
     * <p>
     * The broadcast can be executed in parallel, which can be enabled by setting
     * {@link org.glassfish.tyrus.core.TyrusWebSocketEngine#PARALLEL_BROADCAST_ENABLED}
     * to {@code true} in server properties.
     *
     * @param message message to be broadcasted.
     * @return map of local sessions and futures for user to get the information about status of the message.
     */
    public Map<Session, Future<?>> broadcast(String path, String message) {
        final TyrusEndpointWrapper wrapper = lookupTyrusEndpointWrapper(path);
        if (wrapper != null) {
            return wrapper.broadcast(message);
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Broadcasts binary message to all connected clients, including remote sessions (if any).
     * </p>
     * The broadcast can be executed in parallel, which can be enabled by setting
     * {@link org.glassfish.tyrus.core.TyrusWebSocketEngine#PARALLEL_BROADCAST_ENABLED}
     * to {@code true} in server properties.
     *
     * @param message message to be broadcasted.
     * @return map of local sessions and futures for user to get the information about status of the message.
     */
    public Map<Session, Future<?>> broadcast(String path, ByteBuffer message) {
        final TyrusEndpointWrapper wrapper = lookupTyrusEndpointWrapper(path);
        if (wrapper != null) {
            return wrapper.broadcast(message);
        } else {
            return Collections.emptyMap();
        }
    }

    private TyrusEndpointWrapper lookupTyrusEndpointWrapper(String path) {
        for (TyrusEndpointWrapper wrapper : endpointWrappers) {
            if (wrapper.getEndpointPath().equals(path)) {
                return wrapper;
            }
        }

        return null;
    }
}