package br.com.myrank.repository;

import br.com.myrank.domain.entity.CreatorPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorPhotoRepository extends JpaRepository<CreatorPhoto, CreatorPhoto.Id> {
}
