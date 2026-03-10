package fun.prof_chen.teamviewer.multipleplayeresp.sync.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface CrudRepository<ID, T> {
	Optional<T> findById(ID id);

	Map<ID, T> snapshot();

	Collection<T> findAll();

	boolean contains(ID id);

	boolean isEmpty();

	void put(ID id, T value);

	void putAll(Map<ID, T> values);

	T remove(ID id);

	void removeAll(Collection<ID> ids);

	void clear();
}