/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;

import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumns;
import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.FetchMode;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.annotations.PropertyBinder;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.ToOne;

import java.util.ArrayList;
import java.util.List;

import static org.hibernate.cfg.AnnotatedColumn.checkPropertyConsistency;
import static org.hibernate.cfg.AnnotationBinder.matchIgnoreNotFoundWithFetchType;
import static org.hibernate.cfg.BinderHelper.getCascadeStrategy;
import static org.hibernate.cfg.BinderHelper.getFetchMode;
import static org.hibernate.cfg.BinderHelper.getPath;
import static org.hibernate.cfg.BinderHelper.isEmptyAnnotationValue;
import static org.hibernate.internal.CoreLogging.messageLogger;
import static org.hibernate.internal.util.StringHelper.isNotEmpty;
import static org.hibernate.internal.util.StringHelper.nullIfEmpty;
import static org.hibernate.internal.util.StringHelper.qualify;

/**
 * @author Emmanuel Bernard
 */
public class ToOneBinder {
	private static final CoreMessageLogger LOG = messageLogger( ToOneBinder.class );

	static void bindManyToOne(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			MetadataBuildingContext context,
			XProperty property,
			AnnotatedJoinColumn[] joinColumns,
			PropertyBinder propertyBinder,
			boolean forcePersist) {
		final ManyToOne manyToOne = property.getAnnotation( ManyToOne.class );

		//check validity
		if ( property.isAnnotationPresent( Column.class )
				|| property.isAnnotationPresent( Columns.class ) ) {
			throw new AnnotationException(
					"Property '"+ getPath( propertyHolder, inferredData )
							+ "' is a '@ManyToOne' association and may not use '@Column' to specify column mappings (use '@JoinColumn' instead)"
			);
		}

		final Cascade hibernateCascade = property.getAnnotation( Cascade.class );
		final NotFound notFound = property.getAnnotation( NotFound.class );
		final NotFoundAction notFoundAction = notFound == null ? null : notFound.action();
		matchIgnoreNotFoundWithFetchType( propertyHolder.getEntityName(), property.getName(), notFoundAction, manyToOne.fetch() );
		final OnDelete onDelete = property.getAnnotation( OnDelete.class );
		final JoinTable joinTable = propertyHolder.getJoinTable( property );
		if ( joinTable != null ) {
			final Join join = propertyHolder.addJoin( joinTable, false );
			for ( AnnotatedJoinColumn joinColumn : joinColumns ) {
				joinColumn.setExplicitTableName( join.getTable().getName() );
			}
		}
		final boolean mandatory = isMandatory( manyToOne.optional(), property, notFoundAction );
		bindManyToOne(
				getCascadeStrategy( manyToOne.cascade(), hibernateCascade, false, forcePersist ),
				joinColumns,
				!mandatory,
				notFoundAction,
				onDelete != null && OnDeleteAction.CASCADE == onDelete.action(),
				getTargetEntity( inferredData, context ),
				propertyHolder,
				inferredData,
				false,
				isIdentifierMapper,
				inSecondPass,
				propertyBinder,
				context
		);
	}

	private static boolean isMandatory(boolean optional, XProperty property, NotFoundAction notFoundAction) {
		// @MapsId means the columns belong to the pk;
		// A @MapsId association (obviously) must be non-null when the entity is first persisted.
		// If a @MapsId association is not mapped with @NotFound(IGNORE), then the association
		// is mandatory (even if the association has optional=true).
		// If a @MapsId association has optional=true and is mapped with @NotFound(IGNORE) then
		// the association is optional.
		// @OneToOne(optional = true) with @PKJC makes the association optional.
		return !optional
				|| property.isAnnotationPresent( Id.class )
				|| property.isAnnotationPresent( MapsId.class ) && notFoundAction != NotFoundAction.IGNORE;
	}

	private static void bindManyToOne(
			String cascadeStrategy,
			AnnotatedJoinColumn[] columns,
			boolean optional,
			NotFoundAction notFoundAction,
			boolean cascadeOnDelete,
			XClass targetEntity,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean unique, // identifies a "logical" @OneToOne
			boolean isIdentifierMapper,
			boolean inSecondPass,
			PropertyBinder propertyBinder,
			MetadataBuildingContext context) {
		// All FK columns should be in the same table
		final org.hibernate.mapping.ManyToOne value =
				new org.hibernate.mapping.ManyToOne( context, columns[0].getTable() );
		if ( unique ) {
			// This is a @OneToOne mapped to a physical o.h.mapping.ManyToOne
			value.markAsLogicalOneToOne();
		}
		value.setReferencedEntityName( getReferenceEntityName( inferredData, targetEntity, context ) );
		final XProperty property = inferredData.getProperty();
		defineFetchingStrategy( value, property );
		//value.setFetchMode( fetchMode );
		value.setNotFoundAction( notFoundAction );
		value.setCascadeDeleteEnabled( cascadeOnDelete );
		//value.setLazy( fetchMode != FetchMode.JOIN );
		if ( !optional ) {
			for ( AnnotatedJoinColumn column : columns ) {
				column.setNullable( false );
			}
		}

		if ( property.isAnnotationPresent( MapsId.class ) ) {
			//read only
			for ( AnnotatedJoinColumn column : columns ) {
				column.setInsertable( false );
				column.setUpdatable( false );
			}
		}

		boolean hasSpecjManyToOne = handleSpecjSyntax( columns, inferredData, context, property );
		value.setTypeName( inferredData.getClassOrElementName() );
		final String propertyName = inferredData.getPropertyName();
		value.setTypeUsingReflection( propertyHolder.getClassName(), propertyName );

		final String fullPath = qualify( propertyHolder.getPath(), propertyName );

		bindForeignKeyNameAndDefinition( value, property, propertyHolder.getOverriddenForeignKey( fullPath ), context );

		final FkSecondPass secondPass = new ToOneFkSecondPass(
				value,
				columns,
				!optional && unique, //cannot have nullable and unique on certain DBs like Derby
				propertyHolder.getPersistentClass(),
				fullPath,
				context
		);
		if ( inSecondPass ) {
			secondPass.doSecondPass( context.getMetadataCollector().getEntityBindingMap() );
		}
		else {
			context.getMetadataCollector().addSecondPass( secondPass );
		}

		processManyToOneProperty(
				cascadeStrategy,
				columns,
				optional,
				propertyHolder,
				inferredData,
				isIdentifierMapper,
				propertyBinder,
				value,
				property,
				hasSpecjManyToOne,
				propertyName
		);
	}

	private static boolean handleSpecjSyntax(
			AnnotatedJoinColumn[] columns,
			PropertyData inferredData,
			MetadataBuildingContext context,
			XProperty property) {
		//Make sure that JPA1 key-many-to-one columns are read only too
		boolean hasSpecjManyToOne = false;
		if ( context.getBuildingOptions().isSpecjProprietarySyntaxEnabled() ) {
			final JoinColumn joinColumn = property.getAnnotation( JoinColumn.class );
			String columnName = "";
			for ( XProperty prop : inferredData.getDeclaringClass()
					.getDeclaredProperties( AccessType.FIELD.getType() ) ) {
				if ( prop.isAnnotationPresent( Id.class ) && prop.isAnnotationPresent( Column.class ) ) {
					columnName = prop.getAnnotation( Column.class ).name();
				}

				if ( property.isAnnotationPresent( ManyToOne.class ) && joinColumn != null
						&& ! isEmptyAnnotationValue( joinColumn.name() )
						&& joinColumn.name().equals( columnName )
						&& !property.isAnnotationPresent( MapsId.class ) ) {
					hasSpecjManyToOne = true;
					for ( AnnotatedJoinColumn column : columns) {
						column.setInsertable( false );
						column.setUpdatable( false );
					}
				}
			}
		}
		return hasSpecjManyToOne;
	}

	private static void processManyToOneProperty(
			String cascadeStrategy,
			AnnotatedJoinColumn[] columns,
			boolean optional,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean isIdentifierMapper,
			PropertyBinder propertyBinder,
			org.hibernate.mapping.ManyToOne value, XProperty property,
			boolean hasSpecjManyToOne,
			String propertyName) {

		checkPropertyConsistency( columns, qualify( propertyHolder.getEntityName(), propertyName ) );

		//PropertyBinder binder = new PropertyBinder();
		propertyBinder.setName( propertyName );
		propertyBinder.setValue( value );
		//binder.setCascade(cascadeStrategy);
		if (isIdentifierMapper) {
			propertyBinder.setInsertable( false );
			propertyBinder.setUpdatable( false );
		}
		else if (hasSpecjManyToOne) {
			propertyBinder.setInsertable( false );
			propertyBinder.setUpdatable( false );
		}
		else {
			propertyBinder.setInsertable( columns[0].isInsertable() );
			propertyBinder.setUpdatable( columns[0].isUpdatable() );
		}
		propertyBinder.setColumns( columns );
		propertyBinder.setAccessType( inferredData.getDefaultAccess() );
		propertyBinder.setCascade( cascadeStrategy );
		propertyBinder.setProperty( property );
		propertyBinder.setXToMany( true );

		final JoinColumn joinColumn = property.getAnnotation( JoinColumn.class );
		final JoinColumns joinColumns = property.getAnnotation( JoinColumns.class );
		propertyBinder.makePropertyAndBind()
				.setOptional( optional && isNullable( joinColumns, joinColumn ) );
	}

	private static boolean isNullable(JoinColumns joinColumns, JoinColumn joinColumn) {
		if ( joinColumn != null ) {
			return joinColumn.nullable();
		}
		else if ( joinColumns != null ) {
			for ( JoinColumn column : joinColumns.value() ) {
				if ( column.nullable() ) {
					return true;
				}
			}
			return false;
		}
		else {
			return true;
		}
	}

	static void defineFetchingStrategy(ToOne toOne, XProperty property) {
		final FetchType fetchType = getJpaFetchType( property );

		final LazyToOne lazy = property.getAnnotation( LazyToOne.class );
		final NotFound notFound = property.getAnnotation( NotFound.class );
		if ( notFound != null ) {
			toOne.setLazy( false );
			toOne.setUnwrapProxy( true );
		}
		else if ( lazy != null ) {
			toOne.setLazy( lazy.value() != LazyToOneOption.FALSE );
			toOne.setUnwrapProxy( ( lazy.value() == LazyToOneOption.NO_PROXY ) );
		}
		else {
			toOne.setLazy( fetchType == FetchType.LAZY );
			toOne.setUnwrapProxy( fetchType != FetchType.LAZY );
			toOne.setUnwrapProxyImplicit( true );
		}

		final Fetch fetch = property.getAnnotation( Fetch.class );
		if ( fetch != null ) {
			// Hibernate @Fetch annotation takes precedence
			if ( fetch.value() == org.hibernate.annotations.FetchMode.JOIN ) {
				toOne.setFetchMode( FetchMode.JOIN );
				toOne.setLazy( false );
				toOne.setUnwrapProxy( false );
			}
			else if ( fetch.value() == org.hibernate.annotations.FetchMode.SELECT ) {
				toOne.setFetchMode( FetchMode.SELECT );
			}
			else if ( fetch.value() == org.hibernate.annotations.FetchMode.SUBSELECT ) {
				throw new AnnotationException( "Association '" + property.getName()
						+ "' is annotated '@Fetch(SUBSELECT)' but is not many-valued");
			}
			else {
				throw new AssertionFailure( "Unknown FetchMode: " + fetch.value() );
			}
		}
		else {
			toOne.setFetchMode( getFetchMode( fetchType ) );
		}
	}

	private static FetchType getJpaFetchType(XProperty property) {
		final ManyToOne manyToOne = property.getAnnotation( ManyToOne.class );
		final OneToOne oneToOne = property.getAnnotation( OneToOne.class );
		if ( manyToOne != null ) {
			return manyToOne.fetch();
		}
		else if ( oneToOne != null ) {
			return oneToOne.fetch();
		}
		else {
			throw new AssertionFailure("Define fetch strategy on a property not annotated with @OneToMany nor @OneToOne");
		}
	}

	static void bindOneToOne(
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			MetadataBuildingContext context,
			XProperty property,
			AnnotatedJoinColumn[] joinColumns,
			PropertyBinder propertyBinder,
			boolean forcePersist) {
		final OneToOne oneToOne = property.getAnnotation( OneToOne.class );

		//check validity
		if ( property.isAnnotationPresent( Column.class )
				|| property.isAnnotationPresent( Columns.class ) ) {
			throw new AnnotationException(
					"Property '"+ getPath( propertyHolder, inferredData )
							+ "' is a '@OneToOne' association and may not use '@Column' to specify column mappings"
							+ " (use '@PrimaryKeyJoinColumn' instead)"
			);
		}

		//FIXME support a proper PKJCs
		final boolean trueOneToOne = property.isAnnotationPresent( PrimaryKeyJoinColumn.class )
				|| property.isAnnotationPresent( PrimaryKeyJoinColumns.class );
		final Cascade hibernateCascade = property.getAnnotation( Cascade.class );
		final NotFound notFound = property.getAnnotation( NotFound.class );
		final NotFoundAction notFoundAction = notFound == null ? null : notFound.action();

		final boolean mandatory = isMandatory( oneToOne.optional(), property, notFoundAction );
		matchIgnoreNotFoundWithFetchType( propertyHolder.getEntityName(), property.getName(), notFoundAction, oneToOne.fetch() );
		final OnDelete onDelete = property.getAnnotation( OnDelete.class );
		final JoinTable joinTable = propertyHolder.getJoinTable(property);
		if ( joinTable != null ) {
			final Join join = propertyHolder.addJoin( joinTable, false );
			if ( notFoundAction != null ) {
				join.disableForeignKeyCreation();
			}
			for ( AnnotatedJoinColumn joinColumn : joinColumns) {
				joinColumn.setExplicitTableName( join.getTable().getName() );
			}
		}
		bindOneToOne(
				getCascadeStrategy( oneToOne.cascade(), hibernateCascade, oneToOne.orphanRemoval(), forcePersist ),
				joinColumns,
				!mandatory,
				getFetchMode( oneToOne.fetch() ),
				notFoundAction,
				onDelete != null && OnDeleteAction.CASCADE == onDelete.action(),
				getTargetEntity( inferredData, context ),
				propertyHolder,
				inferredData,
				oneToOne.mappedBy(),
				trueOneToOne,
				isIdentifierMapper,
				inSecondPass,
				propertyBinder,
				context
		);
	}

	private static void bindOneToOne(
			String cascadeStrategy,
			AnnotatedJoinColumn[] joinColumns,
			boolean optional,
			FetchMode fetchMode,
			NotFoundAction notFoundAction,
			boolean cascadeOnDelete,
			XClass targetEntity,
			PropertyHolder propertyHolder,
			PropertyData inferredData,
			String mappedBy,
			boolean trueOneToOne,
			boolean isIdentifierMapper,
			boolean inSecondPass,
			PropertyBinder propertyBinder,
			MetadataBuildingContext context) {
		//column.getTable() => persistentClass.getTable()
		final String propertyName = inferredData.getPropertyName();
		LOG.tracev( "Fetching {0} with {1}", propertyName, fetchMode );
		if ( isMapToPK( joinColumns, propertyHolder, trueOneToOne ) || !isEmptyAnnotationValue( mappedBy ) ) {
			//is a true one-to-one
			//FIXME referencedColumnName ignored => ordering may fail.
			final OneToOneSecondPass secondPass = new OneToOneSecondPass(
					mappedBy,
					propertyHolder.getEntityName(),
					propertyName,
					propertyHolder,
					inferredData,
					targetEntity,
					notFoundAction,
					cascadeOnDelete,
					optional,
					cascadeStrategy,
					joinColumns,
					context
			);
			if ( inSecondPass ) {
				secondPass.doSecondPass( context.getMetadataCollector().getEntityBindingMap() );
			}
			else {
				context.getMetadataCollector().addSecondPass( secondPass, isEmptyAnnotationValue( mappedBy ) );
			}
		}
		else {
			//has a FK on the table
			bindManyToOne(
					cascadeStrategy,
					joinColumns,
					optional,
					notFoundAction,
					cascadeOnDelete,
					targetEntity,
					propertyHolder,
					inferredData,
					true,
					isIdentifierMapper,
					inSecondPass,
					propertyBinder,
					context
			);
		}
	}

	private static boolean isMapToPK(AnnotatedJoinColumn[] joinColumns, PropertyHolder propertyHolder, boolean trueOneToOne) {
		if ( trueOneToOne ) {
			return true;
		}
		else {
			//try to find a hidden true one to one (FK == PK columns)
			KeyValue identifier = propertyHolder.getIdentifier();
			if ( identifier == null ) {
				//this is a @OneToOne in an @EmbeddedId (the persistentClass.identifier is not set yet, it's being built)
				//by definition the PK cannot refer to itself so it cannot map to itself
				return false;
			}
			else {
				List<String> idColumnNames = new ArrayList<>();
				if ( identifier.getColumnSpan() != joinColumns.length ) {
					return false;
				}
				else {
					for ( org.hibernate.mapping.Column currentColumn: identifier.getColumns() ) {
						idColumnNames.add( currentColumn.getName() );
					}
					for ( AnnotatedJoinColumn col: joinColumns) {
						if ( !idColumnNames.contains( col.getMappingColumn().getName() ) ) {
							return false;
						}
					}
					return true;
				}
			}
		}
	}

	public static void bindForeignKeyNameAndDefinition(
			SimpleValue value,
			XProperty property,
			ForeignKey foreignKey,
			MetadataBuildingContext context) {
		if ( property.getAnnotation( NotFound.class ) != null ) {
			// supersedes all others
			value.disableForeignKey();
		}
		else {
			final JoinColumn joinColumn = property.getAnnotation( JoinColumn.class );
			final JoinColumns joinColumns = property.getAnnotation( JoinColumns.class );
			if ( joinColumn!=null && noConstraint( joinColumn.foreignKey(), context )
					|| joinColumns!=null && noConstraint( joinColumns.foreignKey(), context ) ) {
				value.disableForeignKey();
			}
			else {
				final org.hibernate.annotations.ForeignKey fk =
						property.getAnnotation( org.hibernate.annotations.ForeignKey.class );
				if ( fk != null && isNotEmpty( fk.name() ) ) {
					value.setForeignKeyName( fk.name() );
				}
				else {
					if ( noConstraint( foreignKey, context ) ) {
						value.disableForeignKey();
					}
					else if ( foreignKey != null ) {
						value.setForeignKeyName( nullIfEmpty( foreignKey.name() ) );
						value.setForeignKeyDefinition( nullIfEmpty( foreignKey.foreignKeyDefinition() ) );
					}
					else if ( joinColumns != null ) {
						value.setForeignKeyName( nullIfEmpty( joinColumns.foreignKey().name() ) );
						value.setForeignKeyDefinition( nullIfEmpty( joinColumns.foreignKey().foreignKeyDefinition() ) );
					}
					else if ( joinColumn != null ) {
						value.setForeignKeyName( nullIfEmpty( joinColumn.foreignKey().name() ) );
						value.setForeignKeyDefinition( nullIfEmpty( joinColumn.foreignKey().foreignKeyDefinition() ) );
					}
				}
			}
		}
	}

	private static boolean noConstraint(ForeignKey joinColumns, MetadataBuildingContext context) {
		return joinColumns != null
				&& ( joinColumns.value() == ConstraintMode.NO_CONSTRAINT
					|| joinColumns.value() == ConstraintMode.PROVIDER_DEFAULT
						&& context.getBuildingOptions().isNoConstraintByDefault() );
	}

	public static String getReferenceEntityName(PropertyData propertyData, XClass targetEntity, MetadataBuildingContext context) {
		if ( AnnotationBinder.isDefault( targetEntity, context ) ) {
			return propertyData.getClassOrElementName();
		}
		else {
			return targetEntity.getName();
		}
	}

	public static String getReferenceEntityName(PropertyData propertyData, MetadataBuildingContext context) {
		XClass targetEntity = getTargetEntity( propertyData, context );
		return AnnotationBinder.isDefault( targetEntity, context )
				? propertyData.getClassOrElementName()
				: targetEntity.getName();
	}

	public static XClass getTargetEntity(PropertyData propertyData, MetadataBuildingContext context) {
		return context.getBootstrapContext().getReflectionManager()
				.toXClass( getTargetEntityClass( propertyData.getProperty() ) );
	}

	private static Class<?> getTargetEntityClass(XProperty property) {
		final ManyToOne mTo = property.getAnnotation( ManyToOne.class );
		if (mTo != null) {
			return mTo.targetEntity();
		}
		final OneToOne oTo = property.getAnnotation( OneToOne.class );
		if (oTo != null) {
			return oTo.targetEntity();
		}
		throw new AssertionFailure("Unexpected discovery of a targetEntity: " + property.getName() );
	}
}
