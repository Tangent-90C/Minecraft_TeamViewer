package fun.prof_chen.teamviewer.main_code.sync.impl.repository;

import fun.prof_chen.teamviewer.main_code.sync.api.CrudRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MapBackedCrudRepository<ID, T> implements CrudRepository<ID, T> {
	private final Map<ID, T> backingMap;

	public MapBackedCrudRepository(Map<ID, T> backingMap) {
		this.backingMap = Objects.requireNonNull(backingMap, "backingMap");
	}

	@Override
	public Optional<T> findById(ID id) {
		return Optional.ofNullable(backingMap.get(id));
	}

	@Override
	public Map<ID, T> snapshot() {
		return new LinkedHashMap<>(backingMap);
	}

	@Override
	public Collection<T> findAll() {
		return snapshot().values();
	}

	@Override
	public boolean contains(ID id) {
		return backingMap.containsKey(id);
	}

	@Override
	public boolean isEmpty() {
		return backingMap.isEmpty();
	}

	@Override
	public void put(ID id, T value) {
		if (id == null || value == null) {
			return;
		}
		backingMap.put(id, value);
	}

	@Override
	public void putAll(Map<ID, T> values) {
		if (values == null || values.isEmpty()) {
			return;
		}
		for (Map.Entry<ID, T> entry : values.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public T remove(ID id) {
		if (id == null) {
			return null;
		}
		return backingMap.remove(id);
	}

	@Override
	public void removeAll(Collection<ID> ids) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		for (ID id : ids) {
			remove(id);
		}
	}

	@Override
	public void clear() {
		backingMap.clear();
	}
}