/*******************************************************************************
 * Copyright (c) 2004 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.xmpp.smack;

import java.io.IOException;
import java.util.Map;

import org.eclipse.ecf.core.comm.DisconnectConnectionEvent;
import org.eclipse.ecf.core.comm.IAsynchConnectionEventHandler;
import org.eclipse.ecf.core.comm.IConnectionEventHandler;
import org.eclipse.ecf.core.comm.ISynchAsynchConnection;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDFactory;
import org.eclipse.ecf.provider.xmpp.Trace;
import org.eclipse.ecf.provider.xmpp.container.IIMMessageSender;
import org.eclipse.ecf.provider.xmpp.identity.XMPPID;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

public class ChatConnection implements ISynchAsynchConnection, IIMMessageSender {

	public static final Trace trace = Trace.create("smackconnection");
	public static final Trace smack = Trace.create("smackdebug");
	protected static final String STRING_ENCODING = "UTF-8";
	public static final String OBJECT_PROPERTY_NAME = ChatConnection.class
			.getName()
			+ ".object";
	protected static final int XMPP_NORMAL_PORT = 5222;
	protected XMPPConnection connection = null;
	protected IAsynchConnectionEventHandler handler = null;
	protected ID localID = null;
	protected boolean isStarted = false;
	protected String serverName;
	protected int serverPort = -1;
	protected Map properties = null;
	protected boolean isConnected = false;

	protected void debug(String msg) {
		if (Trace.ON && trace != null) {
			trace.msg(msg);
		}
	}

	protected void dumpStack(String msg, Throwable t) {
		if (Trace.ON && trace != null) {
			trace.dumpStack(t, msg);
		}
	}

	protected void logException(String msg, Throwable t) {
		dumpStack(msg, t);
	}

	public Map getProperties() {
		return properties;
	}

	public Object getAdapter(Class clazz) {
		if (clazz.equals(IIMMessageSender.class)) {
			return this;
		}
		return null;
	}

	public XMPPConnection getXMPPConnection() {
		return connection;
	}
	public ChatConnection(IAsynchConnectionEventHandler h) {
		this.handler = h;
		if (Trace.create("smackdebug") != null) {
			XMPPConnection.DEBUG_ENABLED = true;
		}
	}

	public synchronized Object connect(ID remote, Object data, int timeout)
			throws IOException {
		if (connection != null)
			throw new IOException("Currently connected");
		debug("connect(" + remote + "," + data + "," + timeout + ")");
		if (timeout > 0)
			SmackConfiguration.setPacketReplyTimeout(timeout);
		XMPPID jabberURI = null;
		try {
			jabberURI = (XMPPID) remote;
		} catch (ClassCastException e) {
			IOException throwMe = new IOException(e.getMessage());
			throwMe.setStackTrace(e.getStackTrace());
			throw throwMe;
		}
		String username = jabberURI.getUsername();
		serverName = jabberURI.getHostname();
		String password = null;
		if (data == null)
			throw new IOException("data parameter (password) must be provided");
		try {
			password = (String) data;
		} catch (ClassCastException e) {
			IOException throwMe = new IOException(e.getClass().getName()
					+ " wrapped: " + e.getMessage());
			throwMe.setStackTrace(e.getStackTrace());
			throw throwMe;
		}
		Roster roster = null;
		try {
			if (serverPort == -1) {
				connection = new XMPPConnection(serverName);
			} else {
				connection = new XMPPConnection(serverName, serverPort);
			}
			// Login
			connection.login(username, (String) data);
			isConnected = true;
			debug("User: " + username + " logged into " + serverName);
			roster = getRoster();
			roster.reload();
		} catch (XMPPException e) {
			if (connection != null) {
				connection.close();
			}
			IOException result = new IOException(e.getMessage());
			result.setStackTrace(e.getStackTrace());
			throw result;
		}
		// Now setup listener
		connection.addConnectionListener(new ConnectionListener() {
			public void connectionClosed() {
				handleConnectionClosed(null);
			}

			public void connectionClosedOnError(Exception e) {
				handleConnectionClosed(e);
			}
		});

		connection.addPacketListener(new PacketListener() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.jivesoftware.smack.PacketListener#processPacket(org.jivesoftware.smack.packet.Packet)
			 */
			public void processPacket(Packet arg0) {
				handlePacket(arg0);
			}
		}, null);
		return roster;
	}

	public synchronized void disconnect() throws IOException {
		debug("disconnect()");
		if (isStarted()) {
			stop();
		}
		if (connection != null) {
			connection.close();
			isConnected = false;
			connection = null;
		}
	}

	public synchronized boolean isConnected() {
		return (isConnected);
	}

	public synchronized ID getLocalID() {
		if (!isConnected())
			return null;
		try {
			return IDFactory.makeID(XMPPID.PROTOCOL, new Object[] { connection
					.getConnectionID() });
		} catch (Exception e) {
			logException("Exception in getLocalID", e);
			return null;
		}
	}

	public synchronized void start() {
		if (isStarted())
			return;
		isStarted = true;
	}

	public boolean isStarted() {
		return isStarted;
	}

	public synchronized void stop() {
		isStarted = false;
	}

	protected void handleConnectionClosed(Exception e) {
		handler.handleDisconnectEvent(new DisconnectConnectionEvent(this, e,
				null));
	}

	protected void handlePacket(Packet arg0) {
		debug("handlePacket(" + arg0 + ")");
		try {
			Object val = arg0.getProperty(OBJECT_PROPERTY_NAME);
			if (val != null) {
				handler.handleAsynchEvent(new ChatConnectionObjectPacketEvent(
						this, arg0, val));
			} else {
				handler.handleAsynchEvent(new ChatConnectionPacketEvent(this,
						arg0));
			}
		} catch (IOException e) {
			logException("Exception in handleAsynchEvent", e);
			try {
				disconnect();
			} catch (Exception e1) {
				logException("Exception in disconnect()", e1);
			}
		}
	}

	protected void sendMessage(ID receiver, Message msg) throws IOException {
		synchronized (this) {
			if (!isConnected())
				throw new IOException("not connected");
			try {
				if (receiver == null) {
					throw new IOException(
							"receiver cannot be null for normal xmpp instant messaging");
				} else {
					msg.setType(Message.Type.CHAT);
					Chat localChat = connection.createChat(receiver.getName());
					localChat.sendMessage(msg);
				}
			} catch (XMPPException e) {
				IOException result = new IOException(
						"XMPPException in sendMessage: " + e.getMessage());
				result.setStackTrace(e.getStackTrace());
				throw result;
			}
		}
	}

	public synchronized void sendAsynch(ID receiver, byte[] data)
			throws IOException {
		if (data == null)
			throw new IOException("no data");
		debug("sendAsynch(" + receiver + "," + data + ")");
		Message aMsg = new Message();
		aMsg.setProperty(OBJECT_PROPERTY_NAME, data);
		sendMessage(receiver, aMsg);
	}

	public synchronized Object sendSynch(ID receiver, byte[] data)
			throws IOException {
		if (data == null)
			throw new IOException("data cannot be null");
		// This is assumed to be disconnect...so we'll just disconnect
		// disconnect();
		return null;
	}

	public void addCommEventListener(IConnectionEventHandler listener) {
		// XXX Not yet implemented
	}

	public void removeCommEventListener(IConnectionEventHandler listener) {
		// XXX Not yet implemented
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.provider.xmpp.IIMMessageSender#sendMessage(org.eclipse.ecf.core.identity.ID,
	 *      java.lang.String)
	 */
	public void sendMessage(ID target, String message) throws IOException {
		if (target == null)
			throw new IOException("target cannot be null");
		if (message == null)
			throw new IOException("message cannot be null");
		debug("sendMessage(" + target + "," + message + ")");
		Message aMsg = new Message();
		aMsg.setBody(message);
		sendMessage(target, aMsg);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.provider.xmpp.IIMMessageSender#getRoster()
	 */
	public Roster getRoster() throws IOException {
		if (connection == null)
			return null;
		if (!connection.isConnected())
			return null;
		Roster roster = connection.getRoster();
		roster.setSubscriptionMode(Roster.SUBSCRIPTION_ACCEPT_ALL);
		return roster;
	}
}
