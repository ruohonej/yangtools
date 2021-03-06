/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.impl.schema;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.yangtools.concepts.Identifiable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.AttributesBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * Base strategy for converting an instance identifier into a normalized node structure.
 * Use provided static methods for generic YangInstanceIdentifier -> NormalizedNode translation in ImmutableNodes.
 */
abstract class InstanceIdToNodes<T extends PathArgument> implements Identifiable<T> {
    private final T identifier;

    InstanceIdToNodes(final T identifier) {
        this.identifier = identifier;
    }

    @Override
    public final T getIdentifier() {
        return identifier;
    }

    /**
     * Build a strategy for the next path argument.
     *
     * @param child child identifier
     * @return transformation strategy for a specific child
     */
    abstract InstanceIdToNodes<?> getChild(PathArgument child);

    /**
     * Convert instance identifier into a NormalizedNode structure.
     *
     * @param instanceId Instance identifier to transform into NormalizedNodes
     * @param deepestChild Optional normalized node to be inserted as the last child
     * @param operation Optional modify operation to be set on the last child
     * @return NormalizedNode structure corresponding to submitted instance ID
     */
    abstract NormalizedNode<?, ?> create(PathArgument first, Iterator<PathArgument> others,
            Optional<NormalizedNode<?, ?>> deepestChild, Optional<Entry<QName, ModifyAction>> operation);

    abstract boolean isMixin();

    static void addModifyOpIfPresent(final Optional<Entry<QName, ModifyAction>> operation,
            final AttributesBuilder<?> builder) {
        if (operation.isPresent()) {
            final Entry<QName, ModifyAction> entry = operation.get();
            builder.withAttributes(ImmutableMap.of(entry.getKey(), entry.getValue().name().toLowerCase()));
        }
    }

    private static final class UnkeyedListMixinNormalization extends InstanceIdToCompositeNodes<NodeIdentifier> {
        private final UnkeyedListItemNormalization innerNode;

        UnkeyedListMixinNormalization(final ListSchemaNode list) {
            super(NodeIdentifier.create(list.getQName()));
            this.innerNode = new UnkeyedListItemNormalization(list);
        }

        @Override
        CollectionNodeBuilder<UnkeyedListEntryNode, UnkeyedListNode> createBuilder(final PathArgument compositeNode) {
            return Builders.unkeyedListBuilder().withNodeIdentifier(getIdentifier());
        }

        @Override
        InstanceIdToNodes<?> getChild(final PathArgument child) {
            return child.getNodeType().equals(getIdentifier().getNodeType()) ? innerNode : null;
        }

        @Override
        boolean isMixin() {
            return true;
        }
    }

    private static final class AnyXmlNormalization extends InstanceIdToNodes<NodeIdentifier> {
        AnyXmlNormalization(final AnyXmlSchemaNode schema) {
            super(NodeIdentifier.create(schema.getQName()));
        }

        @Override
        InstanceIdToNodes<?> getChild(final PathArgument child) {
            return null;
        }

        @Override
        NormalizedNode<?, ?> create(final PathArgument first, final Iterator<PathArgument> others,
                final Optional<NormalizedNode<?, ?>> deepestChild,
                final Optional<Entry<QName,ModifyAction>> operation) {
            final NormalizedNodeAttrBuilder<NodeIdentifier, DOMSource, AnyXmlNode> builder = Builders.anyXmlBuilder()
                    .withNodeIdentifier(getIdentifier());
            if (deepestChild.isPresent()) {
                final NormalizedNode<?, ?> child = deepestChild.get();
                checkState(child instanceof AnyXmlNode);
                builder.withValue(((AnyXmlNode) child).getValue());
            }

            addModifyOpIfPresent(operation, builder);
            return builder.build();
        }

        @Override
        boolean isMixin() {
            return false;
        }
    }

    private static Optional<DataSchemaNode> findChildSchemaNode(final DataNodeContainer parent, final QName child) {
        DataSchemaNode potential = parent.getDataChildByName(child);
        if (potential == null) {
            potential = findChoice(Iterables.filter(parent.getChildNodes(), ChoiceSchemaNode.class), child);
        }
        return Optional.ofNullable(potential);
    }

    static InstanceIdToNodes<?> fromSchemaAndQNameChecked(final DataNodeContainer schema, final QName child) {
        final Optional<DataSchemaNode> potential = findChildSchemaNode(schema, child);
        checkArgument(potential.isPresent(),
                "Supplied QName %s is not valid according to schema %s, potential children nodes: %s", child, schema,
                schema.getChildNodes());

        final DataSchemaNode result = potential.get();
        // We try to look up if this node was added by augmentation
        if (schema instanceof DataSchemaNode && result.isAugmenting()) {
            return fromAugmentation(schema, (AugmentationTarget) schema, result);
        }
        return fromDataSchemaNode(result);
    }

    private static ChoiceSchemaNode findChoice(final Iterable<ChoiceSchemaNode> choices, final QName child) {
        for (final ChoiceSchemaNode choice : choices) {
            for (final CaseSchemaNode caze : choice.getCases().values()) {
                if (findChildSchemaNode(caze, child).isPresent()) {
                    return choice;
                }
            }
        }
        return null;
    }

    /**
     * Returns a SchemaPathUtil for provided child node
     * <p/>
     * If supplied child is added by Augmentation this operation returns
     * a SchemaPathUtil for augmentation,
     * otherwise returns a SchemaPathUtil for child as
     * call for {@link #fromDataSchemaNode(org.opendaylight.yangtools.yang.model.api.DataSchemaNode)}.
     */
    private static InstanceIdToNodes<?> fromAugmentation(final DataNodeContainer parent,
            final AugmentationTarget parentAug, final DataSchemaNode child) {
        for (final AugmentationSchemaNode aug : parentAug.getAvailableAugmentations()) {
            final DataSchemaNode potential = aug.getDataChildByName(child.getQName());
            if (potential != null) {
                return new InstanceIdToCompositeNodes.AugmentationNormalization(aug, parent);
            }
        }
        return fromDataSchemaNode(child);
    }

    static InstanceIdToNodes<?> fromDataSchemaNode(final DataSchemaNode potential) {
        if (potential instanceof ContainerSchemaNode) {
            return new InstanceIdToCompositeNodes.ContainerTransformation((ContainerSchemaNode) potential);
        } else if (potential instanceof ListSchemaNode) {
            return fromListSchemaNode((ListSchemaNode) potential);
        } else if (potential instanceof LeafSchemaNode) {
            return new InstanceIdToSimpleNodes.LeafNormalization((LeafSchemaNode) potential);
        } else if (potential instanceof ChoiceSchemaNode) {
            return new InstanceIdToCompositeNodes.ChoiceNodeNormalization((ChoiceSchemaNode) potential);
        } else if (potential instanceof LeafListSchemaNode) {
            return fromLeafListSchemaNode((LeafListSchemaNode) potential);
        } else if (potential instanceof AnyXmlSchemaNode) {
            return new AnyXmlNormalization((AnyXmlSchemaNode) potential);
        }
        return null;
    }

    private static InstanceIdToNodes<?> fromListSchemaNode(final ListSchemaNode potential) {
        final List<QName> keyDefinition = potential.getKeyDefinition();
        if (keyDefinition == null || keyDefinition.isEmpty()) {
            return new UnkeyedListMixinNormalization(potential);
        }
        return potential.isUserOrdered() ? new InstanceIdToCompositeNodes.OrderedMapMixinNormalization(potential)
                : new InstanceIdToCompositeNodes.UnorderedMapMixinNormalization(potential);
    }

    private static InstanceIdToNodes<?> fromLeafListSchemaNode(final LeafListSchemaNode potential) {
        return potential.isUserOrdered() ? new InstanceIdToCompositeNodes.OrderedLeafListMixinNormalization(potential)
                : new InstanceIdToCompositeNodes.UnorderedLeafListMixinNormalization(potential);
    }
}
