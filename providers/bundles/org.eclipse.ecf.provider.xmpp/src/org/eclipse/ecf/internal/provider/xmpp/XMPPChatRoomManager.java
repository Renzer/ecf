/****************************************************************************
 * Copyright (c) 2004 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/

package org.eclipse.ecf.internal.provider.xmpp;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.IDCreateException;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.internal.provider.xmpp.identity.XMPPID;
import org.eclipse.ecf.internal.provider.xmpp.identity.XMPPRoomID;
import org.eclipse.ecf.internal.provider.xmpp.smack.ECFConnection;
import org.eclipse.ecf.presence.chatroom.ChatRoomCreateException;
import org.eclipse.ecf.presence.chatroom.IChatRoomContainer;
import org.eclipse.ecf.presence.chatroom.IChatRoomInfo;
import org.eclipse.ecf.presence.chatroom.IChatRoomInvitationListener;
import org.eclipse.ecf.presence.chatroom.IChatRoomManager;
import org.eclipse.ecf.presence.history.IHistory;
import org.eclipse.ecf.presence.history.IHistoryManager;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.muc.HostedRoom;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;

public class XMPPChatRoomManager implements IChatRoomManager {

	/**
	 * 
	 */
	private static final String PROP_XMPP_SUBJECT = "subject";

	// key in the create room configuration in order to find the please to find
	// the conference rooms on the XMPP server
	public static final String PROP_XMPP_CONFERENCE = "conference";

	ID containerID = null;

	Namespace connectNamespace = null;

	Vector invitationListeners = new Vector();

	ECFConnection ecfConnection = null;

	Vector chatrooms = new Vector();

	ID connectedID = null;

	public XMPPChatRoomManager(ID containerID) {
		this.containerID = containerID;
	}

	protected void addChat(IChatRoomContainer container) {
		chatrooms.add(container);
	}

	protected void removeChat(IChatRoomContainer container) {
		chatrooms.remove(container);
	}

	protected ID createRoomIDFromName(String from) {
		try {
			return new XMPPRoomID(connectNamespace, ecfConnection
					.getXMPPConnection(), from);
		} catch (URISyntaxException e) {
			return null;
		}
	}

	protected ID createUserIDFromName(String name) {
		ID result = null;
		try {
			result = new XMPPID(connectNamespace, name);
			return result;
		} catch (Exception e) {
			return null;
		}
	}

	protected void setConnection(Namespace connectNamespace, ID connectedID,
			ECFConnection connection) {
		this.connectNamespace = connectNamespace;
		this.connectedID = connectedID;
		this.ecfConnection = connection;
		if (connection != null) {
			// Setup invitation requestListener
			MultiUserChat.addInvitationListener(ecfConnection
					.getXMPPConnection(), new InvitationListener() {
				public void invitationReceived(XMPPConnection arg0,
						String arg1, String arg2, String arg3, String arg4,
						Message arg5) {
					fireInvitationReceived(createRoomIDFromName(arg1),
							createUserIDFromName(arg2),
							createUserIDFromName(arg5.getTo()), arg5
									.getSubject(), arg3);
				}
			});
		}
	}

	protected void disposeChatRooms() {
		for (Iterator i = chatrooms.iterator(); i.hasNext();) {
			IChatRoomContainer cc = (IChatRoomContainer) i.next();
			cc.dispose();
		}
		chatrooms.clear();
	}

	public void dispose() {
		invitationListeners.clear();
		containerID = null;
		connectNamespace = null;
		disposeChatRooms();
		setConnection(null, null, null);
	}

	class ECFRoomInfo implements IChatRoomInfo {

		RoomInfo info;

		XMPPRoomID roomID;

		ID connectedID;

		public ECFRoomInfo(XMPPRoomID roomID, RoomInfo info, ID connectedID) {
			this.roomID = roomID;
			this.info = info;
			this.connectedID = connectedID;
		}

		public String getDescription() {
			return info.getDescription();
		}

		public String getSubject() {
			return info.getSubject();
		}

		public ID getRoomID() {
			return roomID;
		}

		public int getParticipantsCount() {
			return info.getOccupantsCount();
		}

		public String getName() {
			return roomID.getLongName();
		}

		public boolean isPersistent() {
			return info.isPersistent();
		}

		public boolean requiresPassword() {
			return info.isPasswordProtected();
		}

		public boolean isModerated() {
			return info.isModerated();
		}

		public ID getConnectedID() {
			return roomID;
		}

		public Object getAdapter(Class clazz) {
			return null;
		}

		public IChatRoomContainer createChatRoomContainer()
				throws ContainerCreateException {
			IChatRoomContainer chatContainer = null;
			if (ecfConnection == null)
				throw new ContainerCreateException("disconnected");
			try {
				chatContainer = new XMPPChatRoomContainer(ecfConnection,
						connectNamespace);
				addChat(chatContainer);
				return chatContainer;
			} catch (IDCreateException e) {
				throw new ContainerCreateException(
						"Exception creating chat container", e);
			}
		}

		public String toString() {
			StringBuffer buf = new StringBuffer("ECFRoomInfo[");
			buf.append("id=").append(containerID).append(";name=" + getName());
			buf.append(";service=" + getConnectedID());
			buf.append(";count=" + getParticipantsCount());
			buf.append(";subject=" + getSubject()).append(
					";desc=" + getDescription());
			buf.append(";pers=" + isPersistent()).append(
					";pw=" + requiresPassword());
			buf.append(";mod=" + isModerated()).append("]");
			return buf.toString();
		}
	}

	public IChatRoomManager[] getChildren() {
		return new IChatRoomManager[0];
	}

	protected ID createIDFromHostedRoom(HostedRoom room) {
		try {
			return new XMPPRoomID(connectNamespace, ecfConnection
					.getXMPPConnection(), room.getJid(), room.getName());
		} catch (URISyntaxException e) {
			// debug output
			return null;
		}
	}

	protected IChatRoomContainer findReceiverChatRoom(ID toID) {
		if (toID == null)
			return null;
		XMPPRoomID roomID = null;
		if (toID instanceof XMPPRoomID) {
			roomID = (XMPPRoomID) toID;
			String mucname = roomID.getMucString();
			for (Iterator i = chatrooms.iterator(); i.hasNext();) {
				IChatRoomContainer cont = (IChatRoomContainer) i.next();
				if (cont == null)
					continue;
				ID tid = cont.getConnectedID();
				if (tid != null && tid instanceof XMPPRoomID) {
					XMPPRoomID targetID = (XMPPRoomID) tid;
					String tmuc = targetID.getMucString();
					if (tmuc.equals(mucname)) {
						return cont;
					}
				}
			}
		}
		return null;
	}

	protected ID[] getChatRooms() {
		if (ecfConnection == null)
			return null;
		XMPPConnection conn = ecfConnection.getXMPPConnection();
		if (conn == null)
			return null;
		Collection result = new ArrayList();
		try {
			Collection svcs = MultiUserChat.getServiceNames(conn);
			for (Iterator svcsi = svcs.iterator(); svcsi.hasNext();) {
				String svc = (String) svcsi.next();
				Collection rooms = MultiUserChat.getHostedRooms(conn, svc);
				for (Iterator roomsi = rooms.iterator(); roomsi.hasNext();) {
					HostedRoom room = (HostedRoom) roomsi.next();
					ID roomID = createIDFromHostedRoom(room);
					if (roomID != null)
						result.add(roomID);
				}
			}
		} catch (XMPPException e) {
			return null;
		}
		return (ID[]) result.toArray(new ID[] {});
	}

	protected IChatRoomInfo getChatRoomInfo(ID roomID) {
		if (!(roomID instanceof XMPPRoomID))
			return null;
		XMPPRoomID cRoomID = (XMPPRoomID) roomID;
		try {
			RoomInfo info = MultiUserChat.getRoomInfo(ecfConnection
					.getXMPPConnection(), cRoomID.getMucString());
			if (info != null) {
				return new ECFRoomInfo(cRoomID, info, connectedID);
			}
		} catch (XMPPException e) {
			return null;
		}
		return null;
	}

	public IChatRoomInfo getChatRoomInfo(String roomname) {
		try {
			if (ecfConnection == null)
				return null;
			// Create roomid
			XMPPConnection conn = ecfConnection.getXMPPConnection();
			XMPPRoomID roomID = new XMPPRoomID(connectNamespace, conn, roomname);
			String mucName = roomID.getMucString();
			RoomInfo info = MultiUserChat.getRoomInfo(conn, mucName);
			if (info != null) {
				return new ECFRoomInfo(roomID, info, connectedID);
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public IChatRoomInfo[] getChatRoomInfos() {
		ID[] chatRooms = getChatRooms();
		if (chatRooms == null)
			return null;
		IChatRoomInfo[] res = new IChatRoomInfo[chatRooms.length];
		int count = 0;
		for (int i = 0; i < chatRooms.length; i++) {
			IChatRoomInfo infoResult = getChatRoomInfo(chatRooms[i]);
			if (infoResult != null) {
				res[count++] = infoResult;
			}
		}
		IChatRoomInfo[] results = new IChatRoomInfo[count];
		for (int i = 0; i < count; i++) {
			results[i] = res[i];
		}
		return results;
	}

	public Object getAdapter(Class adapter) {
		return null;
	}

	public void addInvitationListener(IChatRoomInvitationListener listener) {
		invitationListeners.add(listener);
	}

	public void removeInvitationListener(IChatRoomInvitationListener listener) {
		invitationListeners.remove(listener);
	}

	protected void fireInvitationReceived(ID roomID, ID fromID, ID toID,
			String subject, String body) {
		for (Iterator i = invitationListeners.iterator(); i.hasNext();) {
			IChatRoomInvitationListener l = (IChatRoomInvitationListener) i
					.next();
			l.handleInvitationReceived(roomID, fromID, subject, body);
		}
	}

	public IChatRoomManager getParent() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.presence.chatroom.IChatRoomManager#createChatRoom(java.lang.String,
	 *      java.util.Map)
	 */
	public IChatRoomInfo createChatRoom(String roomname, Map properties)
			throws ChatRoomCreateException {
		if (roomname == null)
			throw new ChatRoomCreateException(roomname,
					"roomname cannot be null");
		try {
			String nickname = ecfConnection.getXMPPConnection().getUser();
			String server = ecfConnection.getXMPPConnection().getHost();
			String domain = (properties == null) ? XMPPRoomID.DOMAIN_DEFAULT
					: (String) properties.get(PROP_XMPP_CONFERENCE);
			String conference = XMPPRoomID.fixConferenceDomain(domain, server);
			String roomID = roomname + XMPPRoomID.AT_SIGN + conference;
			// create proxy to the room
			MultiUserChat muc = new MultiUserChat(ecfConnection
					.getXMPPConnection(), roomID);

			if (!checkRoom(conference, roomID)) {
				// otherwise create a new one
				muc.create(nickname);
				muc.sendConfigurationForm(new Form(Form.TYPE_SUBMIT));
				String subject = (properties == null) ? null
						: (String) properties.get(PROP_XMPP_SUBJECT);
				if (subject != null)
					muc.changeSubject(subject);
			}

			String longname = muc.getRoom();
			if (longname == null || longname.length() <= 0) {
				longname = roomID;
			}

			RoomInfo info = MultiUserChat.getRoomInfo(ecfConnection
					.getXMPPConnection(), roomID);

			if (info != null) {
				XMPPRoomID xid = new XMPPRoomID(connectedID.getNamespace(),
						ecfConnection.getXMPPConnection(), roomID, longname);
				return new ECFRoomInfo(xid, info, connectedID);
			} else
				throw new XMPPException("No room info for " + roomID);
		} catch (XMPPException e) {
			throw new ChatRoomCreateException(roomname, e.getMessage(), e);
		} catch (URISyntaxException e) {
			throw new ChatRoomCreateException(roomname, e.getMessage(), e);
		}
	}

	/**
	 * check if the MultiUserChat room is already existing on the XMPP server.
	 * 
	 * @param conference
	 * @param room
	 *            the name of the room
	 * @return true, if the room exists, false otherwise
	 * @throws XMPPException
	 */
	protected boolean checkRoom(String conference, String room)
			throws XMPPException {
		XMPPConnection conn = ecfConnection.getXMPPConnection();
		ServiceDiscoveryManager serviceDiscoveryManager = new ServiceDiscoveryManager(
				conn);
		DiscoverItems result = serviceDiscoveryManager
				.discoverItems(conference);

		for (Iterator items = result.getItems(); items.hasNext();) {
			DiscoverItems.Item item = ((DiscoverItems.Item) items.next());
			if (room.equals(item.getEntityID())) {
				return true;
			}
		}
		return false;
	}

	protected IHistoryManager chatRoomHistoryManager = new IHistoryManager() {

		public IHistory getHistory(ID chatRoomID, Map options) {
			// TODO Auto-generated method stub
			return null;
		}

		public boolean isActive() {
			// TODO Auto-generated method stub
			return false;
		}

		public void setActive(boolean active) {
			// TODO Auto-generated method stub
			
		}

		public Object getAdapter(Class adapter) {
			// TODO Auto-generated method stub
			return null;
		}
		
	};
	
	public IHistoryManager getHistoryManager() {
		return chatRoomHistoryManager;
	}
}
