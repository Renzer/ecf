/*******************************************************************************
 * Copyright (c) 2010 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.osgi.services.remoteserviceadmin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.ecf.osgi.services.remoteserviceadmin.AbstractTopologyManager;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.EndpointDescription;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.IHostContainerSelector;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.RemoteConstants;
import org.eclipse.ecf.remoteservice.IRemoteServiceContainer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.EventHook;
import org.osgi.service.remoteserviceadmin.EndpointListener;

public class TopologyManagerImpl extends AbstractTopologyManager implements
		EventHook, EndpointListener {

	private ServiceRegistration endpointListenerRegistration;

	private ServiceRegistration eventHookRegistration;

	public TopologyManagerImpl(BundleContext context, DiscoveryImpl discovery) {
		super(context, discovery);
	}

	public void start() throws Exception {
		super.start();
		// Register as EndpointListener, so that it gets notified when Endpoints
		// are discovered
		Properties props = new Properties();
		props.put(
				org.osgi.service.remoteserviceadmin.EndpointListener.ENDPOINT_LISTENER_SCOPE,
				"("
						+ org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_ID
						+ "=*)");
		endpointListenerRegistration = getContext().registerService(
				EndpointListener.class.getName(), this, props);

		// Register as EventHook, so that we get notified when remote services
		// are registered
		eventHookRegistration = getContext().registerService(
				EventHook.class.getName(), this, null);
	}

	public void close() {
		if (eventHookRegistration != null) {
			eventHookRegistration.unregister();
			eventHookRegistration = null;
		}
		if (endpointListenerRegistration != null) {
			endpointListenerRegistration.unregister();
			endpointListenerRegistration = null;
		}
		super.close();
	}

	public void endpointAdded(
			org.osgi.service.remoteserviceadmin.EndpointDescription endpoint,
			String matchedFilter) {
		if (endpoint instanceof EndpointDescription) {
			handleEndpointAdded((EndpointDescription) endpoint);
		} else
			logWarning("ECF Topology Manager:  Ignoring Non-ECF endpointAdded="
					+ endpoint + ",matchedFilter=" + matchedFilter);
	}

	public void endpointRemoved(
			org.osgi.service.remoteserviceadmin.EndpointDescription endpoint,
			String matchedFilter) {
		if (endpoint instanceof EndpointDescription) {
			handleEndpointRemoved((EndpointDescription) endpoint);
		} else
			logWarning("ECF Topology Manager:  Ignoring Non-ECF endpointRemoved="
					+ endpoint + ",matchedFilter=" + matchedFilter);
	}

	private void handleEndpointAdded(EndpointDescription endpoint) {
		// TODO Auto-generated method stub
		trace("handleEndpointAdded", "endpoint=" + endpoint);
	}

	private void handleEndpointRemoved(EndpointDescription endpoint) {
		// TODO Auto-generated method stub
		trace("handleEndpointRemoved", "endpoint=" + endpoint);
	}

	private Map<String, Object> prepareExportProperties(
			ServiceReference serviceReference, String[] exportedInterfaces,
			String[] exportedConfigs, String[] serviceIntents,
			IRemoteServiceContainer[] rsContainers) {
		Map<String, Object> result = new HashMap<String, Object>();
		result.put(RemoteConstants.RSA_CONTAINERS, rsContainers);
		result.put(RemoteConstants.RSA_EXPORTED_INTERFACES, exportedInterfaces);
		return result;
	}

	public void event(ServiceEvent event, Collection contexts) {
		switch (event.getType()) {
		case ServiceEvent.MODIFIED:
			handleServiceModifying(event.getServiceReference());
			break;
		case ServiceEvent.MODIFIED_ENDMATCH:
			break;
		case ServiceEvent.REGISTERED:
			handleServiceRegistering(event.getServiceReference());
			break;
		case ServiceEvent.UNREGISTERING:
			handleServiceUnregistering(event.getServiceReference());
			break;
		default:
			break;
		}

	}

	private void handleServiceRegistering(ServiceReference serviceReference) {
		// Using OSGI 4.2 Chap 13 Remote Services spec, get the specified remote
		// interfaces for the given service reference
		String[] exportedInterfaces = getExportedInterfaces(serviceReference);
		// If no remote interfaces set, then we don't do anything with it
		if (exportedInterfaces == null)
			return;

		// Get optional service property for exported configs
		String[] exportedConfigs = getStringArrayFromPropertyValue(serviceReference
				.getProperty(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_EXPORTED_CONFIGS));

		// Get all intents (service.intents, service.exported.intents,
		// service.exported.intents.extra)
		String[] serviceIntents = getServiceIntents(serviceReference);

		// Get a host container selector
		IHostContainerSelector hostContainerSelector = getHostContainerSelector();
		if (hostContainerSelector == null) {
			logError("selectRemoteServiceContainers",
					"No hostContainerSelector available");
			return;
		}
		// select ECF remote service containers that match given exported
		// interfaces, configs, and intents
		IRemoteServiceContainer[] rsContainers = hostContainerSelector
				.selectHostContainers(serviceReference, exportedInterfaces,
						exportedConfigs, serviceIntents);
		// If none found, log a warning and we're done
		if (rsContainers == null || rsContainers.length == 0) {
			logWarning(
					"handleServiceRegistered", //$NON-NLS-1$
					DebugOptions.TOPOLOGY_MANAGER, this.getClass(),
					"No remote service containers found for serviceReference=" //$NON-NLS-1$
							+ serviceReference
							+ ". Remote service NOT EXPORTED"); //$NON-NLS-1$
			return;
		}
		// prepare export properties
		Map<String, Object> exportProperties = prepareExportProperties(
				serviceReference, exportedInterfaces, exportedConfigs,
				serviceIntents, rsContainers);

		// Select remote service admin
		org.osgi.service.remoteserviceadmin.RemoteServiceAdmin rsa = selectRemoteServiceAdmin(
				serviceReference, exportedInterfaces, exportedConfigs,
				serviceIntents, rsContainers);

		// if no remote service admin available, then log error and return
		if (rsa == null) {
			logError("handleServiceRegistered",
					"No RemoteServiceAdmin found for serviceReference="
							+ serviceReference
							+ ".  Remote service NOT EXPORTED");
			return;
		}
		// Export the remote service using the selected remote service admin
		Collection<org.osgi.service.remoteserviceadmin.ExportRegistration> registrations = rsa
				.exportService(serviceReference, exportProperties);

		if (registrations.size() == 0) {
			logError("handleServiceRegistered",
					"No export registrations created by RemoteServiceAdmin="
							+ rsa + ".  ServiceReference=" + serviceReference
							+ " NOT EXPORTED");
			return;
		}

		// publish exported registrations
		publishExportedRegistrations(registrations);

	}

	private void handleServiceModifying(ServiceReference serviceReference) {
		handleServiceUnregistering(serviceReference);
		handleServiceRegistering(serviceReference);
	}

	private void handleServiceUnregistering(ServiceReference serviceReference) {
		// TODO Auto-generated method stub

	}

}