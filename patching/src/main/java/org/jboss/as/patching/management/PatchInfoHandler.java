/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.patching.management;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.Identity;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.tool.PatchingHistory;
import org.jboss.as.patching.tool.PatchingHistory.Entry;
import org.jboss.dmr.ModelNode;

/**
 * This handler returns the info about specific patch
 *
 * @author Alexey Loubyansky
 */
public class PatchInfoHandler extends PatchStreamResourceOperationStepHandler {

    public static final PatchInfoHandler INSTANCE = new PatchInfoHandler();

    @Override
    protected void execute(final OperationContext context, final ModelNode operation, final InstalledIdentity installedIdentity) throws OperationFailedException {

        final ModelNode patchIdNode = PatchResourceDefinition.PATCH_ID_OPTIONAL.resolveModelAttribute(context, operation);
        final String patchId = patchIdNode.isDefined() ? patchIdNode.asString() : null;

        if(patchId == null) {
            final ModelNode readResource = new ModelNode();
            readResource.get("address").set(operation.get("address"));
            readResource.get("operation").set("read-resource");
            readResource.get("recursive").set(true);
            readResource.get("include-runtime").set(true);
            final OperationStepHandler readResHandler = context.getRootResourceRegistration().getOperationHandler(PathAddress.EMPTY_ADDRESS, "read-resource");
            context.addStep(readResource, readResHandler, Stage.MODEL);
        } else {
            final boolean verbose = PatchResourceDefinition.VERBOSE.resolveModelAttribute(context, operation).asBoolean();
            final PatchableTarget.TargetInfo info;
            try {
                info = installedIdentity.getIdentity().loadTargetInfo();
            } catch (Exception e) {
                throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(installedIdentity.getIdentity().getName()), e);
            }

            final PatchingHistory.Iterator i = PatchingHistory.Factory.iterator(installedIdentity, info);
            final ModelNode result = patchIdInfo(context, patchId, verbose, i);
            if (result == null) {
                context.getFailureDescription().set(PatchLogger.ROOT_LOGGER.patchNotFoundInHistory(patchId).getLocalizedMessage());
            }
            context.getResult().set(result);
        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    protected ModelNode patchIdInfo(final OperationContext context, final String patchId, final boolean verbose, final PatchingHistory.Iterator i) {
        while(i.hasNext()) {
            final Entry entry = i.next();
            if(patchId.equals(entry.getPatchId())) {
                final ModelNode result = new ModelNode();
                result.get(Constants.PATCH_ID).set(entry.getPatchId());
                result.get(Constants.TYPE).set(entry.getType().getName());
                result.get(Constants.DESCRIPTION).set(entry.getMetadata().getDescription());
                final String link = entry.getMetadata().getLink();
                if (link != null) {
                    result.get(Constants.LINK).set(link);
                }
                final Identity identity = entry.getMetadata().getIdentity();
                result.get(Constants.IDENTITY_NAME).set(identity.getName());
                result.get(Constants.IDENTITY_VERSION).set(identity.getVersion());

                if(verbose) {
                    final ModelNode list = result.get(Constants.ELEMENTS).setEmptyList();
                    final Patch metadata = entry.getMetadata();
                    for(PatchElement e : metadata.getElements()) {
                        final ModelNode element = new ModelNode();
                        element.get(Constants.PATCH_ID).set(e.getId());
                        element.get(Constants.TYPE).set(e.getProvider().isAddOn() ? Constants.ADD_ON : Constants.LAYER);
                        element.get(Constants.NAME).set(e.getProvider().getName());
                        element.get(Constants.DESCRIPTION).set(e.getDescription());
                        list.add(element);
                    }
                }
                return result;
            }
        }
        return null;
    }
}
