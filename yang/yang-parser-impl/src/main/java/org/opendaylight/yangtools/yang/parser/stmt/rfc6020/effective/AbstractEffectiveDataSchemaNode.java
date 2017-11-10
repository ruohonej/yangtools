/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ConstraintDefinition;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.parser.rfc7950.stmt.AbstractEffectiveSchemaNode;
import org.opendaylight.yangtools.yang.parser.spi.meta.CopyHistory;
import org.opendaylight.yangtools.yang.parser.spi.meta.CopyType;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;

public abstract class AbstractEffectiveDataSchemaNode<D extends DeclaredStatement<QName>> extends
        AbstractEffectiveSchemaNode<D> implements DataSchemaNode {

    private final boolean addedByUses;
    private final boolean configuration;
    private final ConstraintDefinition constraints;

    // FIXME: YANGTOOLS-724: this field should be final
    private boolean augmenting;

    public AbstractEffectiveDataSchemaNode(final StmtContext<QName, D, ?> ctx) {
        super(ctx);
        this.constraints = EffectiveConstraintDefinitionImpl.forParent(this);
        this.configuration = ctx.isConfiguration();

        // initCopyType
        final CopyHistory originalHistory = ctx.getCopyHistory();
        if (originalHistory.contains(CopyType.ADDED_BY_USES_AUGMENTATION)) {
            this.addedByUses = this.augmenting = true;
        } else {
            this.augmenting = originalHistory.contains(CopyType.ADDED_BY_AUGMENTATION);
            this.addedByUses = originalHistory.contains(CopyType.ADDED_BY_USES);
        }
    }

    @Override
    public boolean isAugmenting() {
        return augmenting;
    }

    @Override
    public boolean isAddedByUses() {
        return addedByUses;
    }

    @Override
    public boolean isConfiguration() {
        return configuration;
    }

    @Override
    public ConstraintDefinition getConstraints() {
        return constraints;
    }

    /**
     * Reset {@link #isAugmenting()} to false.
     *
     * @deprecated This method is a violation of immutable contract and is a side-effect of bad/incomplete lifecycle,
     *             which needs to be fixed. Do not introduce new callers. This deficiency is tracked in YANGTOOLS-724.
     */
    @Deprecated
    protected void resetAugmenting() {
        augmenting = false;
    }
}
