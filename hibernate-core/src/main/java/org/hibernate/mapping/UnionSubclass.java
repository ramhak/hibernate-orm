/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;
import java.util.Iterator;
import java.util.List;

import org.hibernate.boot.spi.MetadataBuildingContext;

/**
 * A subclass in a table-per-concrete-class mapping
 * @author Gavin King
 */
public class UnionSubclass extends Subclass implements TableOwner {
	private Table table;

	public UnionSubclass(PersistentClass superclass, MetadataBuildingContext buildingContext) {
		super( superclass, buildingContext );
	}

	public Table getTable() {
		return table;
	}

	public void setTable(Table table) {
		this.table = table;
		getSuperclass().addSubclassTable(table);
	}

	public java.util.Set<String> getSynchronizedTables() {
		return synchronizedTables;
	}

	@Deprecated @SuppressWarnings("deprecation")
	protected Iterator<Property> getNonDuplicatedPropertyIterator() {
		return getPropertyClosureIterator();
	}

	@Override
	protected List<Property> getNonDuplicatedProperties() {
		return getPropertyClosure();
	}
	
	public Table getIdentityTable() {
		return getTable();
	}
	
	public Object accept(PersistentClassVisitor mv) {
		return mv.accept(this);
	}
}
