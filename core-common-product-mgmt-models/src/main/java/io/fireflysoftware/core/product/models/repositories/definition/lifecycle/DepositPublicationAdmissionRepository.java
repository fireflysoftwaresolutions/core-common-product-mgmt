package io.fireflysoftware.core.product.models.repositories.definition.lifecycle;

import io.fireflysoftware.core.product.models.entities.definition.lifecycle.ProductDefinitionDraftRoot;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandNativeRepository.NativeAdmission;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface DepositPublicationAdmissionRepository extends Repository<ProductDefinitionDraftRoot,UUID> {
    @Query("SELECT command_id,replay,command_recorded_at,action_id,retained_action_recorded_at FROM public.begin_product_definition_deposit_publication(:tenant,:root,:operation,:preparation,:evidence,:issuer,:actor,:actorUntil,:permitUntil,:original,:digest,:plan)")
    Mono<NativeAdmission> begin(@Param("tenant") UUID tenant,@Param("root") UUID root,
            @Param("operation") UUID operation,@Param("preparation") UUID preparation,
            @Param("evidence") String evidence,@Param("issuer") String issuer,@Param("actor") String actor,
            @Param("actorUntil") long actorUntil,@Param("permitUntil") long permitUntil,
            @Param("original") String original,@Param("digest") String digest,@Param("plan") String plan);
}
