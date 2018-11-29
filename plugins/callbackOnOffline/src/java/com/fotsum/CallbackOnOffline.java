package com.fotsum;

import okhttp3.*;
import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
//import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Future;

public class CallbackOnOffline implements Plugin, PacketInterceptor {

    private static final Logger Log = LoggerFactory.getLogger(CallbackOnOffline.class);

    private static final String PROPERTY_DEBUG = "plugin.callback_on_offline.debug";
    private static final String PROPERTY_URL = "plugin.callback_on_offline.url";
    private static final String PROPERTY_TOKEN = "plugin.callback_on_offline.token";
    private static final String PROPERTY_SEND_BODY = "plugin.callback_on_offline.send_body";

    private boolean debug;
    private boolean sendBody;

    private String url;
    private String token;
    private InterceptorManager interceptorManager;
    private UserManager userManager;
    private PresenceManager presenceManager;
    private Client client;

    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        debug = JiveGlobals.getBooleanProperty(PROPERTY_DEBUG, false);
        sendBody = JiveGlobals.getBooleanProperty(PROPERTY_SEND_BODY, true);

        url = getProperty(PROPERTY_URL, "http://localhost:8080/user/offline/callback/url");
        token = getProperty(PROPERTY_TOKEN, UUID.randomUUID().toString());

        if (debug) {
            Log.debug("Value of sendBody property", sendBody);
            Log.debug("initialize CallbackOnOffline plugin. Start.");
            Log.debug("Loaded properties: \nurl={}, \ntoken={}, \nsendBody={}", new Object[]{url, token, sendBody});
        }

        interceptorManager = InterceptorManager.getInstance();
        presenceManager = XMPPServer.getInstance().getPresenceManager();
        userManager = XMPPServer.getInstance().getUserManager();
        client = ClientBuilder.newClient();

        // register with interceptor manager
        interceptorManager.addInterceptor(this);

        if (debug) {
            Log.debug("initialize CallbackOnOffline plugin. Finish.");
        }
    }

    private String getProperty(String code, String defaultSetValue) {
        String value = JiveGlobals.getProperty(code, null);
        if (value == null || value.length() == 0) {
            JiveGlobals.setProperty(code, defaultSetValue);
            value = defaultSetValue;
        }

        return value;
    }

    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
        if (debug) {
            Log.debug("destroy CallbackOnOffline plugin.");
        }
    }


    public void interceptPacket(Packet packet, Session session, boolean incoming,
                                boolean processed) throws PacketRejectedException {
        if (processed
                && incoming
                && packet instanceof Message
                && packet.getTo() != null) {

            Message msg = (Message) packet;
            JID to = packet.getTo();

            if (msg.getType() != Message.Type.chat) {
                return;
            }

            if (msg.getBody() == null || msg.getBody().isEmpty()) {
                return;
            }

            try {
                User userTo = userManager.getUser(to.getNode());
                boolean available = presenceManager.isAvailable(userTo);

                if (debug) {
                    Log.debug(" Hello here is our packet ", packet.toString());
                    Log.debug("intercepted message from {} to {}, recipient is available {}", new Object[]{packet.getFrom().toBareJID(), to.toBareJID(), available});
                }

                if (!available) {
                    JID from = packet.getFrom();
                    String body = sendBody ? msg.getBody() : null;

                    WebTarget target = client.target(url);

                    if (debug) {
                        Log.debug("This is the body ",msg.getBody());
                        Log.debug("sending request to url='{}'", target);
                    }

                    MessageData data = new MessageData(token, from.toBareJID(), to.toBareJID(), body);

                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");

                    String jsonString = String.format("{\"token\":\"%s\",\"body\":\"%s\",\"to\":\"%s\", \"from\": \"%s\"}", token, body, to.toBareJID(), from.toBareJID());
                    RequestBody requestBody = RequestBody.create(JSON, jsonString);
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder()
                            .url(url)
                            .post(requestBody)
                            .build();

                    Response okResponse = client.newCall(request).execute();
                    Log.debug(String.format("got response okhttp ======= %s", okResponse.body().string()));

//                    Entity<MessageData> json = Entity.json(data);
//                    if (debug) {
//                        Log.debug(" this log is from DD =====================");
//                        Log.debug(data.toString());
//                        Log.debug(json.toString());
//                        Log.debug(" this log is from DD =====================");
//                    }
//                    Future<Response> responseFuture = target
//                            .request()
//                            .async()
//                            .post(json);
//
//                    if (debug) {
//                        try {
//                            Response response = responseFuture.get();
//                            Log.debug("got response status url='{}' status='{}'", target, response.getStatus());
//                        } catch (Exception e) {
//                            Log.debug("can't get response status url='{}'", target, e);
//                        }
//                    }
                }
            } catch (UserNotFoundException e) {
                if (debug) {
                    Log.debug("can't find user with name: " + to.getNode());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
