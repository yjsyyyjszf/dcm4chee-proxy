/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2012
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.proxy;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.net.Device;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.proxy.audit.AuditLog;
import org.dcm4chee.proxy.conf.PIXConsumer;
import org.dcm4chee.proxy.conf.ProxyDevice;
import org.dcm4chee.proxy.dimse.CEcho;
import org.dcm4chee.proxy.dimse.CFind;
import org.dcm4chee.proxy.dimse.CGet;
import org.dcm4chee.proxy.dimse.CMove;
import org.dcm4chee.proxy.dimse.CStore;
import org.dcm4chee.proxy.dimse.Mpps;
import org.dcm4chee.proxy.dimse.StgCmt;
import org.dcm4chee.proxy.forward.Scheduler;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class Proxy extends DeviceService<ProxyDevice> implements ProxyMBean {

    public static final String KS_TYPE = "org.dcm4chee.proxy.net.keyStoreType";
    public static final String KS_URL = "org.dcm4chee.proxy.net.keyStoreURL";
    public static final String KS_PASSWORD = "org.dcm4chee.proxy.net.storePassword";
    public static final String KEY_PASSWORD = "org.dcm4chee.proxy.net.keyPassword";

    private final HL7Configuration dicomConfiguration;
    private PIXConsumer pixConsumer;
    private static Scheduler scheduler;

    public Proxy(HL7Configuration dicomConfiguration, ProxyDevice proxyDevice) throws ConfigurationException,
            Exception {
        this.dicomConfiguration = dicomConfiguration;
        init(proxyDevice);
        device.setAeCache(new ApplicationEntityCache(dicomConfiguration));
        device.setHl7AppCache(new HL7ApplicationCache(dicomConfiguration));
        setPixConsumer(new PIXConsumer(device.getHl7AppCache()));
        device.setDimseRQHandler(serviceRegistry());
    }

    public PIXConsumer getPixConsumer() {
        return pixConsumer;
    }

    public void setPixConsumer(PIXConsumer pixConsumer) {
        this.pixConsumer = pixConsumer;
    }

    @Override
    public void start() throws Exception {
        scheduler = new Scheduler((ProxyDevice) device, new AuditLog());
        super.start();
        scheduler.start();
    }

    @Override
    public void stop() {
        scheduler.stop();
        super.stop();
    }

    protected DicomServiceRegistry serviceRegistry() {
        DicomServiceRegistry dcmService = new DicomServiceRegistry();
        dcmService.addDicomService(new CEcho());
        dcmService.addDicomService(new CStore("*"));
        dcmService.addDicomService(new StgCmt());
        dcmService.addDicomService(new CFind(pixConsumer, "1.2.840.10008.5.1.4.1.2.1.1", "1.2.840.10008.5.1.4.1.2.2.1",
                "1.2.840.10008.5.1.4.1.2.3.1", "1.2.840.10008.5.1.4.31"));
        dcmService.addDicomService(new CGet(pixConsumer, "1.2.840.10008.5.1.4.1.2.1.3", "1.2.840.10008.5.1.4.1.2.2.3",
                "1.2.840.10008.5.1.4.1.2.3.3"));
        dcmService.addDicomService(new CMove(pixConsumer, "1.2.840.10008.5.1.4.1.2.1.2", "1.2.840.10008.5.1.4.1.2.2.2",
                "1.2.840.10008.5.1.4.1.2.3.2"));
        dcmService.addDicomService(new Mpps());
        return dcmService;
    }

    @Override
    public void reloadConfiguration() throws Exception {
        scheduler.stop();
        device.reconfigure(dicomConfiguration.findDevice(device.getDeviceName()));
        device.rebindConnections();
        scheduler.start();
    }

    @Override
    public Device unwrapDevice() {
        return device;
    }
}
