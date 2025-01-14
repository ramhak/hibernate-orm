/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.annotations.override.inheritance;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.cfg.AnnotationBinder;
import org.hibernate.cfg.annotations.EntityBinder;
import org.hibernate.internal.CoreMessageLogger;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.EntityManagerFactoryBasedFunctionalTest;
import org.hibernate.testing.logger.LoggerInspectionRule;
import org.hibernate.testing.logger.Triggerable;
import org.junit.Rule;
import org.junit.jupiter.api.Test;

import org.jboss.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Vlad Mihalcea
 */
@TestForIssue(jiraKey = "HHH-12609, HHH-12654, HHH-13172")
public class EntityInheritanceAttributeOverrideTest extends EntityManagerFactoryBasedFunctionalTest {

	@Rule
	public LoggerInspectionRule logInspection = new LoggerInspectionRule(
			Logger.getMessageLogger( CoreMessageLogger.class, EntityBinder.class.getName() ) );

	@Override
	public Class<?>[] getAnnotatedClasses() {
		return new Class[] {
				CategoryEntity.class,
				TaxonEntity.class,
				AbstractEntity.class
		};
	}

	@Override
	public EntityManagerFactory produceEntityManagerFactory() {
		Triggerable warningLogged = logInspection.watchForLogMessages( "HHH000499:" );

		EntityManagerFactory entityManagerFactory = super.produceEntityManagerFactory();

		assertTrue( warningLogged.wasTriggered(), "A warning should have been logged for this unsupported configuration");
		return entityManagerFactory;
	}

	@Test
	public void test() {
		produceEntityManagerFactory().close();
	}

	@Entity(name = "AbstractEntity")
	@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
	public static class AbstractEntity {

		@Id
		private Long id;

		@Column(name = "code", nullable = false, unique = true)
		private String code;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}

	}

	@Entity(name = "Category")
	public static class CategoryEntity extends AbstractEntity {

	}

	@Entity(name = "Taxon")
	@Table(
		name = "taxon",
		uniqueConstraints = @UniqueConstraint(name = "category_code", columnNames = { "catalog_version_id", "code" })
	)
	@AttributeOverride(name = "code", column = @Column(name = "code", nullable = false, unique = false))
	public static class TaxonEntity extends CategoryEntity {

		@Column(name = "catalog_version_id")
		private String catalogVersion;

		public String getCatalogVersion() {
			return catalogVersion;
		}

		public void setCatalogVersion(String catalogVersion) {
			this.catalogVersion = catalogVersion;
		}
	}
}
