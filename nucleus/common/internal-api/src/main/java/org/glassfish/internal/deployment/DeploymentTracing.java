/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2006-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
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
// Portions Copyright [2019] Payara Foundation and/or its affiliates
package org.glassfish.internal.deployment;

import com.sun.enterprise.module.Module;
import com.sun.enterprise.module.ModuleState;
import com.sun.enterprise.module.ModulesRegistry;
import org.glassfish.internal.deployment.analysis.StructuredDeploymentTracing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringBufferInputStream;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracing facility for all the deployment backend activities.
 *
 * @author Jerome Dochez
 */
public class DeploymentTracing {

    private final StructuredDeploymentTracing structured;

    public enum Mark {
        ARCHIVE_OPENED,
        ARCHIVE_HANDLER_OBTAINED,
        INITIAL_CONTEXT_CREATED,
        APPINFO_PROVIDED,
        DOL_LOADED,
        APPNAME_DETERMINED,
        TARGET_VALIDATED,
        CONTEXT_CREATED,
        DEPLOY,
        CLASS_LOADER_HIERARCHY,
        PARSING_DONE,
        CLASS_LOADER_CREATED,
        CONTAINERS_SETUP_DONE,
        PREPARE,
        PREPARED,
        LOAD,
        LOAD_EVENTS,
        LOADED,
        START,
        START_EVENTS,
        STARTED,
        REGISTRATION

    }

    public enum AppStage {
        OPENING_ARCHIVE,
        VALIDATE_TARGET,
        INIT_ARCHIVE_HANDLER,
        CREATE_DEPLOY_CONTEXT,
        DETERMINE_APP_NAME,
        PROVIDE_APPINFO,
        READ_DESCRIPTORS, CLEANUP, SWITCH_VERSIONS, DEPLOY, PREPARE, INITIALIZE, REGISTRATION, CLASS_SCANNING, CONTAINER_START,
        LOAD_METADATA, CREATE_CLASSLOADER, PREPARE_MODULES, LOAD, PREPARE_EVENTS, LOAD_EVENTS, START, START_EVENTS;

    }

    public enum ModuleMark {
        PREPARE,
        PREPARE_EVENTS,
        PREPARED,
        LOAD,
        LOADED,
        START,
        STARTED

    }

    public enum ContainerMark {
        SNIFFER_DONE,
        BEFORE_CONTAINER_SETUP,
        AFTER_CONTAINER_SETUP,
        GOT_CONTAINER,
        GOT_DEPLOYER,
        PREPARE,
        PREPARED,
        LOAD,
        LOADED,
        START,
        STARTED
    }

    final long inception = System.currentTimeMillis();

    public DeploymentTracing(StructuredDeploymentTracing structured) {
        this.structured = structured;
    }

    public void close() {
        structured.close();
    }

    public long elapsed() {
        return System.currentTimeMillis() - inception;
    }

    public void addMark(Mark mark) {
        structured.addApplicationMark(mark);
    }

    public void addContainerMark(ContainerMark mark, String name) {
        structured.addContainerMark(name, mark);
    }

    public void addModuleMark(ModuleMark mark, String moduleName) {
        structured.addModuleMark(moduleName, mark);
    }

    public void print(PrintStream ps) {
        structured.print(ps);
    }

    public static void printModuleStatus(ModulesRegistry registry, Level level, Logger logger)
    {
        if (!logger.isLoggable(level)) {

            return;
        }
        int counter=0;

        StringBuilder sb = new StringBuilder("Module Status Report Begins\n");
        // first started :

        for (Module m : registry.getModules()) {
            if (m.getState()== ModuleState.READY) {
                sb.append(m).append("\n");
                counter++;
            }
        }
        sb.append("there were " + counter + " modules in ACTIVE state");
        sb.append("\n");
        counter=0;
        // then resolved
        for (Module m : registry.getModules()) {
            if (m.getState()== ModuleState.RESOLVED) {
                sb.append(m).append("\n");
                counter++;
            }
        }
        sb.append("there were " + counter + " modules in RESOLVED state");
        sb.append("\n");
        counter=0;
        // finally installed
        for (Module m : registry.getModules()) {
            if (m.getState()!= ModuleState.READY && m.getState()!=ModuleState.RESOLVED) {
                sb.append(m).append("\n");
                counter++;
            }
        }
        sb.append("there were " + counter + " modules in INSTALLED state");
        sb.append("Module Status Report Ends");
        logger.log(level, sb.toString());
    }    
}
