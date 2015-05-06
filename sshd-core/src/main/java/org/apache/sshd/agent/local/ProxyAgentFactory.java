/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.agent.local;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.agent.SshAgentServer;
import org.apache.sshd.common.Channel;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.FactoryManagerUtils;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.server.session.ServerSession;

public class ProxyAgentFactory implements SshAgentFactory {

    private final Map<String, AgentServerProxy> proxies = new ConcurrentHashMap<String, AgentServerProxy>();

    public NamedFactory<Channel> getChannelForwardingFactory() {
        return new ChannelAgentForwarding.Factory();
    }

    public SshAgent createClient(FactoryManager manager) throws IOException {
        String proxyId = FactoryManagerUtils.getString(manager, SshAgent.SSH_AUTHSOCKET_ENV_NAME);
        if (GenericUtils.isEmpty(proxyId)) {
            throw new IllegalStateException("No " + SshAgent.SSH_AUTHSOCKET_ENV_NAME + " environment variable set");
        }

        AgentServerProxy proxy = proxies.get(proxyId);
        if (proxy == null) {
            throw new IllegalStateException("No ssh agent found for ID=" + proxyId);
        }

        return proxy.createClient();
    }

    public SshAgentServer createServer(ConnectionService service) throws IOException {
        Session session = service.getSession();
        if (!(session instanceof ServerSession)) {
            throw new IllegalStateException("The session used to create an agent server proxy must be a server session");
        }

        final AgentServerProxy proxy = new AgentServerProxy(service);
        proxies.put(proxy.getId(), proxy);
        return new SshAgentServer() {
            @Override
            public String getId() {
                return proxy.getId();
            }

            @SuppressWarnings("synthetic-access")
            @Override
            public void close() throws IOException {
                proxies.remove(proxy.getId());
                proxy.close();
            }
        };
    }

}
