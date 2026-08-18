package com.caloly.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {
    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms") query: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 25,
        @Query("lc") languageCode: String,
        @Query("cc") countryCode: String,
        @Query("sort_by") sortBy: String = "unique_scans_n",
        @Query("fields") fields: String = SEARCH_FIELDS,
    ): OffSearchResponse

    @GET("cgi/search.pl")
    suspend fun searchByBrand(
        @Query("tagtype_0") tagType: String = "brands",
        @Query("tag_contains_0") tagContains: String = "contains",
        @Query("tag_0") brand: String,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 25,
        @Query("lc") languageCode: String,
        @Query("cc") countryCode: String,
        @Query("sort_by") sortBy: String = "unique_scans_n",
        @Query("fields") fields: String = SEARCH_FIELDS,
    ): OffSearchResponse

    @GET("api/v2/product/{barcode}.json")
    suspend fun productByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = PRODUCT_FIELDS,
    ): OffProductResponse

    companion object {
        const val SEARCH_FIELDS = "code,product_name,product_name_tr,product_name_en,product_name_fr,product_name_de,brands,quantity,serving_size,image_front_small_url,nutriments,last_modified_t,lang,countries_tags"
        const val PRODUCT_FIELDS = SEARCH_FIELDS
    }
}

data class OffSearchResponse(
    val products: List<OffProduct> = emptyList(),
    val count: Int? = null,
    val page: Int? = null,
    @SerializedName("page_size") val pageSize: Int? = null,
    @SerializedName("page_count") val pageCount: Int? = null,
)

data class OffProductResponse(
    val status: String? = null,
    val product: OffProduct? = null,
)

data class OffProduct(
    val code: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("product_name_tr") val productNameTr: String? = null,
    @SerializedName("product_name_en") val productNameEn: String? = null,
    @SerializedName("product_name_fr") val productNameFr: String? = null,
    @SerializedName("product_name_de") val productNameDe: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    @SerializedName("serving_size") val servingSize: String? = null,
    @SerializedName("image_front_small_url") val imageUrl: String? = null,
    val nutriments: OffNutriments? = null,
    @SerializedName("last_modified_t") val lastModifiedAt: Long? = null,
    val lang: String? = null,
    @SerializedName("countries_tags") val countriesTags: List<String>? = null,
)

data class OffNutriments(
    @SerializedName("energy-kcal_100g") val calories100g: Double? = null,
    @SerializedName("proteins_100g") val protein100g: Double? = null,
    @SerializedName("carbohydrates_100g") val carbs100g: Double? = null,
    @SerializedName("fat_100g") val fat100g: Double? = null,
)
