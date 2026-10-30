package com.pettown.business.pet.mapper;

import com.pettown.business.pet.entity.Pet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPetMapper implements PetMapper {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Pet> pets = new ConcurrentHashMap<>();

    @Override
    public Pet insert(String name, String species) {
        Long id = sequence.incrementAndGet();
        Pet pet = new Pet(id, name, species);
        pets.put(id, pet);
        return pet;
    }

    @Override
    public Optional<Pet> findById(Long id) {
        return Optional.ofNullable(pets.get(id));
    }

    @Override
    public List<Pet> findAll() {
        return pets.values().stream().sorted(Comparator.comparing(Pet::id)).toList();
    }
}
