/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.query.sqm.param;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.criteria.Path;
import javax.persistence.metamodel.SingularAttribute;

import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.metamodel.model.domain.JpaMetamodel;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaParameterExpression;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.spi.QueryImplementor;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.NotImplementedYet;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;

/**
 * @author Steve Ebersole
 */
@DomainModel( annotatedClasses = CompositeParameterTests.SimpleEntity.class )
@SessionFactory
public class CompositeParameterTests {
	@Test
	public void testSimplePredicateHql(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.createQuery( "from SimpleEntity where composite = :param" )
					.setParameter( "param", new SimpleComposite() )
					.list();
		});
	}

	@Test
	public void testInPredicateHql(SessionFactoryScope scope) {
		scope.inTransaction( (session) -> {
			session.createQuery( "from SimpleEntity where composite in (:param)" )
					.setParameter( "param", new SimpleComposite() )
					.list();
		});
	}

	@Test
	@NotImplementedYet
	public void testSimplePredicateCriteria(SessionFactoryScope scope) {
		final HibernateCriteriaBuilder builder = scope.getSessionFactory().getCriteriaBuilder();
		final JpaMetamodel jpaMetamodel = scope.getSessionFactory().getRuntimeMetamodels().getJpaMetamodel();
		final EntityDomainType<SimpleEntity> entityDescriptor = jpaMetamodel.entity( SimpleEntity.class );
		final SingularAttribute<? super SimpleEntity, SimpleComposite> attribute = entityDescriptor.getSingularAttribute( "composite", SimpleComposite.class );

		scope.inTransaction( (session) -> {
			final JpaCriteriaQuery<SimpleEntity> criteria = builder.createQuery( SimpleEntity.class );
			final JpaRoot<SimpleEntity> root = criteria.from( entityDescriptor );
			final Path<SimpleComposite> attrPath = root.get( attribute );
			final JpaParameterExpression<SimpleComposite> parameter = builder.parameter( SimpleComposite.class );
			criteria.where( builder.equal( attrPath, parameter ) );

			session.createQuery( criteria ).setParameter( parameter, new SimpleComposite() ).list();
		});

		scope.inTransaction( (session) -> {
			session.createQuery( "from SimpleEntity where composite = :param" )
					.setParameter( "param", new SimpleComposite() )
					.list();
		});
	}

	@Test
	@NotImplementedYet
	public void testInPredicateCriteria(SessionFactoryScope scope) {
		final HibernateCriteriaBuilder builder = scope.getSessionFactory().getCriteriaBuilder();
		final JpaMetamodel jpaMetamodel = scope.getSessionFactory().getRuntimeMetamodels().getJpaMetamodel();
		final EntityDomainType<SimpleEntity> entityDescriptor = jpaMetamodel.entity( SimpleEntity.class );
		final SingularAttribute<? super SimpleEntity, SimpleComposite> attribute = entityDescriptor.getSingularAttribute( "composite", SimpleComposite.class );

		scope.inTransaction( (session) -> {
			final JpaCriteriaQuery<SimpleEntity> criteria = builder.createQuery( SimpleEntity.class );
			final JpaRoot<SimpleEntity> root = criteria.from( entityDescriptor );
			final Path<SimpleComposite> attrPath = root.get( attribute );
			final JpaParameterExpression<SimpleComposite> parameter = builder.parameter( SimpleComposite.class );
			criteria.where( builder.in( attrPath, parameter ) );

			session.createQuery( criteria ).setParameter( parameter, new SimpleComposite() ).list();
		});

		scope.inTransaction( (session) -> {
			session.createQuery( "from SimpleEntity where composite = :param" )
					.setParameter( "param", new SimpleComposite() )
					.list();
		});
	}

	@Test
	@NotImplementedYet
	public void testDeTypedInPredicateCriteria(SessionFactoryScope scope) {
		final HibernateCriteriaBuilder builder = scope.getSessionFactory().getCriteriaBuilder();
		final JpaMetamodel jpaMetamodel = scope.getSessionFactory().getRuntimeMetamodels().getJpaMetamodel();
		final EntityDomainType entityDescriptor = jpaMetamodel.entity( SimpleEntity.class );
		final SingularAttribute attribute = entityDescriptor.getSingularAttribute( "composite" );

		scope.inTransaction( (session) -> {
			final JpaCriteriaQuery criteria = builder.createQuery( SimpleEntity.class );
			final JpaRoot root = criteria.from( entityDescriptor );
			final Path attrPath = root.get( attribute );
			final JpaParameterExpression parameter = builder.parameter( SimpleComposite.class );
			criteria.where( builder.in( attrPath, parameter ) );

			final QueryImplementor query = session.createQuery( criteria );
			query.setParameter( parameter, new SimpleComposite() );
			query.list();
		});
	}

	@Entity(name = "SimpleEntity")
	@Table(name = "simple_entity")
	public class SimpleEntity {
		@Id
		public Integer id;
		public String name;
		public SimpleComposite composite;

		public SimpleEntity() {
		}

		public SimpleEntity(Integer id, String name) {
			this.id = id;
			this.name = name;
		}

		public SimpleEntity(Integer id, String name, SimpleComposite composite) {
			this.id = id;
			this.name = name;
			this.composite = composite;
		}
	}

	@Embeddable
	public class SimpleComposite {
		public String value1;
		public String value2;

		public SimpleComposite() {
		}

		public SimpleComposite(String value1, String value2) {
			this.value1 = value1;
			this.value2 = value2;
		}
	}
}
