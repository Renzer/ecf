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

package org.eclipse.ecf.internal.provider.xmpp.ui.hyperlink;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.IContainerManager;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.internal.provider.xmpp.ui.Activator;
import org.eclipse.ecf.internal.provider.xmpp.ui.Messages;
import org.eclipse.ecf.internal.provider.xmpp.ui.wizards.XMPPConnectWizard;
import org.eclipse.ecf.internal.provider.xmpp.ui.wizards.XMPPSConnectWizard;
import org.eclipse.ecf.presence.IPresenceContainerAdapter;
import org.eclipse.ecf.presence.im.IChatManager;
import org.eclipse.ecf.presence.im.IChatMessageSender;
import org.eclipse.ecf.presence.im.ITypingMessageSender;
import org.eclipse.ecf.presence.roster.IRosterManager;
import org.eclipse.ecf.presence.ui.MessagesView;
import org.eclipse.ecf.provider.xmpp.XMPPContainer;
import org.eclipse.ecf.provider.xmpp.XMPPSContainer;
import org.eclipse.ecf.provider.xmpp.identity.XMPPID;
import org.eclipse.ecf.provider.xmpp.identity.XMPPSID;
import org.eclipse.ecf.ui.IConnectWizard;
import org.eclipse.ecf.ui.hyperlink.AbstractURLHyperlink;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;

/**
 * 
 */
public class XMPPHyperlink extends AbstractURLHyperlink {

	private static final String ECF_XMPP_CONTAINER_NAME = "ecf.xmpp.smack"; //$NON-NLS-1$
	private static final String ECF_XMPPS_CONTAINER_NAME = "ecf.xmpps.smack"; //$NON-NLS-1$
	private static final IContainer[] EMPTY = new IContainer[0];

	boolean isXMPPS;

	protected IContainer[] getContainers() {
		final IContainerManager manager = Activator.getDefault().getContainerManager();
		if (manager == null)
			return EMPTY;
		final List results = new ArrayList();
		final IContainer[] containers = manager.getAllContainers();
		for (int i = 0; i < containers.length; i++) {
			final ID connectedID = containers[i].getConnectedID();
			// Must be connected and ID of correct type
			if (connectedID != null && ((isXMPPS && containers[i] instanceof XMPPSContainer) || (!isXMPPS && containers[i] instanceof XMPPContainer)))
				results.add(containers[i]);
		}
		return (IContainer[]) results.toArray(EMPTY);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.ui.hyperlink.AbstractURLHyperlink#open()
	 */
	public void open() {
		final IContainer[] containers = getContainers();
		if (containers.length > 0) {
			chooseAccount(containers);
		} else {
			if (MessageDialog.openQuestion(null, Messages.XMPPHyperlink_CONNECT_ACCOUNT_DIALOG_TITLE, NLS.bind(Messages.XMPPHyperlink_CONNECT_ACCOUNT_DIALOG_MESSAGE, getURI().getAuthority()))) {
				super.open();
			}
		}
	}

	/**
	 * @param adapters
	 */
	private void chooseAccount(final IContainer[] containers) {
		// If there's only one choice then use it
		if (containers.length == 1) {
			openContainer(containers[0]);
			return;
		} else {
			final IPresenceContainerAdapter[] adapters = new IPresenceContainerAdapter[containers.length];
			for (int i = 0; i < containers.length; i++)
				adapters[i] = (IPresenceContainerAdapter) containers[i].getAdapter(IPresenceContainerAdapter.class);
			final ListDialog dialog = new ListDialog(null);
			dialog.setContentProvider(new IStructuredContentProvider() {

				public Object[] getElements(Object inputElement) {
					return adapters;
				}

				public void dispose() {
				}

				public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
				}
			});
			dialog.setInput(adapters);
			dialog.setAddCancelButton(true);
			dialog.setBlockOnOpen(true);
			dialog.setTitle(Messages.XMPPHyperlink_SELECT_ACCOUNT_TITLE);
			dialog.setMessage(Messages.XMPPHyperlink_SELECT_ACCOUNT_MESSAGE);
			dialog.setHeightInChars(adapters.length > 4 ? adapters.length : 4);
			dialog.setInitialSelections(new IPresenceContainerAdapter[] {adapters[0]});
			dialog.setLabelProvider(new ILabelProvider() {
				public Image getImage(Object element) {
					return null;
				}

				public String getText(Object element) {
					final IRosterManager manager = ((IPresenceContainerAdapter) element).getRosterManager();
					if (manager == null)
						return null;
					return manager.getRoster().getUser().getID().getName();
				}

				public void addListener(ILabelProviderListener listener) {
				}

				public void dispose() {
				}

				public boolean isLabelProperty(Object element, String property) {
					return false;
				}

				public void removeListener(ILabelProviderListener listener) {
				}
			});
			final int result = dialog.open();
			if (result == ListDialog.OK) {
				final Object[] res = dialog.getResult();
				if (res.length > 0)
					openContainer((IContainer) res[0]);
			}
		}
	}

	private void openMessagesView(IChatManager chatManager, ID localID, ID targetID) throws PartInitException {
		final IChatMessageSender icms = chatManager.getChatMessageSender();
		final ITypingMessageSender itms = chatManager.getTypingMessageSender();
		final IWorkbenchWindow ww = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		final MessagesView view = (MessagesView) ww.getActivePage().showView(MessagesView.VIEW_ID);
		view.selectTab(icms, itms, localID, targetID);
	}

	/**
	 * @param presenceContainerAdapter
	 */
	private void openContainer(IContainer container) {
		final IPresenceContainerAdapter presenceContainerAdapter = (IPresenceContainerAdapter) container.getAdapter(IPresenceContainerAdapter.class);
		final IChatManager chatManager = presenceContainerAdapter.getChatManager();
		final IRosterManager rosterManager = presenceContainerAdapter.getRosterManager();
		if (chatManager != null && rosterManager != null) {
			try {
				// get local ID
				final XMPPID localID = (XMPPID) rosterManager.getRoster().getUser().getID();
				final Namespace ns = container.getConnectNamespace();
				// create target ID
				final XMPPID targetID = (isXMPPS) ? new XMPPSID(ns, getURI().getAuthority()) : new XMPPID(ns, getURI().getAuthority());
				// If they are same, just tell user and return
				if (localID.equals(targetID)) {
					MessageDialog.openError(null, Messages.XMPPHyperlink_MESSAGING_ERROR_TITLE, Messages.XMPPHyperlink_MESSAGING_ERROR_MESSAGE);
					return;
				} else {
					final String localHost = localID.getHostname();
					final String targetHost = targetID.getHostname();
					// If the hosts are the same for the target and local
					// accounts,
					// it's pretty obvious that we wish to message to them
					if (localHost.equals(targetHost)) {
						openMessagesView(chatManager, localID, targetID);
					} else {
						// Otherwise, ask the user whether messaging, or
						// connecting is desired
						final MessageDialog messageDialog = new MessageDialog(null, Messages.XMPPHyperlink_SELECT_ACTION_DIALOG_TITLE, null, NLS.bind(Messages.XMPPHyperlink_SELECT_ACTION_DIALOG_MESSAGE, new Object[] {targetHost, localHost, targetID.getName(), localID.getName()}), MessageDialog.QUESTION, new String[] {Messages.XMPPHyperlink_SELECT_ACTION_DIALOG_BUTTON_SEND_MESSAGE, Messages.XMPPHyperlink_SELECT_ACTION_DIALOG_BUTTON_CONNECT, Messages.XMPPHyperlink_SELECT_ACTION_DIALOG_BUTTON_CANCEL}, 2);
						final int selected = messageDialog.open();
						switch (selected) {
							case 0 :
								openMessagesView(chatManager, localID, targetID);
								return;
							case 1 :
								super.open();
								return;
							default :
								return;

						}
					}
				}
			} catch (final Exception e) {
				MessageDialog.openError(null, Messages.XMPPHyperlink_ERROR_OPEN_MESSAGE_VIEW_DIALOG_TITLE, NLS.bind(Messages.XMPPHyperlink_ERROR_OPEN_MESSAGE_VIEW_DIALOG_MESSAGE, e.getLocalizedMessage()));
				Activator.getDefault().getLog().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, IStatus.ERROR, Messages.XMPPHyperlink_ERROR_OPEN_MESSAGE_VIEW_LOG_STATUS_MESSAGE, e));
			}
		}
	}

	/**
	 * Creates a new URL hyperlink.
	 * 
	 * @param region
	 * @param uri
	 */
	public XMPPHyperlink(IRegion region, URI uri) {
		super(region, uri);
		isXMPPS = getURI().getScheme().equalsIgnoreCase(XMPPHyperlinkDetector.XMPPS_PROTOCOL);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.ui.hyperlink.AbstractURLHyperlink#createConnectWizard()
	 */
	protected IConnectWizard createConnectWizard() {
		final String auth = getURI().getAuthority();
		if (isXMPPS)
			return new XMPPSConnectWizard(auth);
		else
			return new XMPPConnectWizard(auth);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ecf.ui.hyperlink.AbstractURLHyperlink#createContainer()
	 */
	protected IContainer createContainer() throws ContainerCreateException {
		return ContainerFactory.getDefault().createContainer((isXMPPS) ? ECF_XMPPS_CONTAINER_NAME : ECF_XMPP_CONTAINER_NAME);
	}

}
