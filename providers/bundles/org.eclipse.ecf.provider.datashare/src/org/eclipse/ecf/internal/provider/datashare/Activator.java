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

package org.eclipse.ecf.internal.provider.datashare;

import org.eclipse.core.runtime.IAdapterManager;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ecf.core.IContainerManager;
import org.eclipse.ecf.core.util.LogHelper;
import org.eclipse.ecf.core.util.PlatformHelper;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The main plugin class to be used in the desktop.
 */
public class Activator implements BundleActivator {

	public static final String PLUGIN_ID = "org.eclipse.ecf.provider.datashare"; //$NON-NLS-1$

	//The shared instance.
	private static Activator plugin;

	private BundleContext context = null;

	private ServiceTracker logServiceTracker = null;

	private ServiceTracker adapterManagerTracker = null;

	private ServiceTracker containerManagerTracker = null;

	/**
	 * The constructor.
	 */
	public Activator() {
		plugin = this;
	}

	protected LogService getLogService() {
		if (logServiceTracker == null) {
			logServiceTracker = new ServiceTracker(this.context, LogService.class.getName(), null);
			logServiceTracker.open();
		}
		return (LogService) logServiceTracker.getService();
	}

	public void log(IStatus status) {
		LogService logService = getLogService();
		if (logService != null) {
			logService.log(LogHelper.getLogCode(status), LogHelper.getLogMessage(status), status.getException());
		}
	}

	/**
	 * This method is called upon plug-in activation
	 */
	public void start(BundleContext ctxt) throws Exception {
		this.context = ctxt;
	}

	/**
	 * This method is called when the plug-in is stopped
	 */
	public void stop(BundleContext ctxt) throws Exception {
		if (logServiceTracker != null) {
			logServiceTracker.close();
			logServiceTracker = null;
		}
		if (adapterManagerTracker != null) {
			adapterManagerTracker.close();
			adapterManagerTracker = null;
		}
		if (containerManagerTracker != null) {
			containerManagerTracker.close();
			containerManagerTracker = null;
		}
		this.context = null;
		plugin = null;
	}

	/**
	 * Returns the shared instance.
	 *
	 * @return the shared instance.
	 */
	public synchronized static Activator getDefault() {
		if (plugin == null) {
			plugin = new Activator();
		}
		return plugin;
	}

	public IAdapterManager getAdapterManager() {
		// First, try to get the adapter manager via
		if (adapterManagerTracker == null) {
			adapterManagerTracker = new ServiceTracker(this.context, IAdapterManager.class.getName(), null);
			adapterManagerTracker.open();
		}
		IAdapterManager adapterManager = (IAdapterManager) adapterManagerTracker.getService();
		// Then, if the service isn't there, try to get from Platform class via
		// PlatformHelper class
		if (adapterManager == null)
			adapterManager = PlatformHelper.getPlatformAdapterManager();
		return adapterManager;
	}

	/**
	 * @return IContainerManager instance
	 */
	public IContainerManager getContainerManager() {
		if (containerManagerTracker == null) {
			containerManagerTracker = new ServiceTracker(this.context, IContainerManager.class.getName(), null);
			containerManagerTracker.open();
		}
		return (IContainerManager) containerManagerTracker.getService();
	}

}
