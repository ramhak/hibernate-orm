/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.internal;

import java.util.function.Consumer;

import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.query.NavigablePath;
import org.hibernate.query.named.ModelPartResultMemento;
import org.hibernate.query.named.ModelPartResultMementoBasic;
import org.hibernate.query.results.ImplicitModelPartResultBuilder;
import org.hibernate.query.results.ResultBuilder;

/**
 * @author Steve Ebersole
 */
public class ImplicitModelPartResultMemento implements ModelPartResultMemento {
	private final NavigablePath navigablePath;
	private final ModelPart referencedModelPart;

	public ImplicitModelPartResultMemento(NavigablePath navigablePath, ModelPart referencedModelPart) {
		this.navigablePath = navigablePath;
		this.referencedModelPart = referencedModelPart;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public ModelPart getReferencedModelPart() {
		return referencedModelPart;
	}

	@Override
	public ResultBuilder resolve(
			Consumer<String> querySpaceConsumer,
			ResultSetMappingResolutionContext context) {
		return new ImplicitModelPartResultBuilder(
				navigablePath,
				null,
				referencedModelPart
		);
	}
}