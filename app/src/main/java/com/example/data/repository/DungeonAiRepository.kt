package com.example.data.repository

import android.util.Log
import com.example.data.model.AppLanguage
import com.example.data.model.BranchingNarrativeEvent
import com.example.data.model.CommunityMod
import com.example.data.model.EnemyBlueprint
import com.example.data.model.GeneratedStage
import com.example.data.model.HeroArchetype
import com.example.data.model.NarrativeChoice
import com.example.data.model.StageObstacle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingConfig
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ModAiReview(
    val loreEn: String,
    val loreId: String,
    val balanceVerdict: String
)

class DungeonAiRepository {

    private val stageSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Atmospheric gothic chamber name"),
            "omenWhisper" to Schema.string("Short chilling 1-sentence omen for this floor"),
            "biomeTheme" to Schema.enumeration(
                listOf("CRIMSON_CRYPT", "BONE_CATHEDRAL", "VOID_SANCTUM", "ASHEN_OSSUARY"),
                "Lighting and visual mood"
            ),
            "ambientColorHex" to Schema.string("Dark hex background e.g. #14121A"),
            "torchColorHex" to Schema.string("Torch light hex e.g. #D4A359"),
            "fogIntensity" to Schema.double("Between 0.25 and 0.65"),
            "obstacles" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "xRatio" to Schema.double("Normalized X between 0.18 and 0.82"),
                        "yRatio" to Schema.double("Normalized Y between 0.28 and 0.72"),
                        "radius" to Schema.double("Between 24.0 and 42.0"),
                        "kind" to Schema.enumeration(listOf("PILLAR", "STATUE", "BRAZIER", "ALTAR"))
                    )
                ),
                description = "3 to 5 gothic arena obstacles"
            ),
            "enemies" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string("Unique dark fantasy monster name"),
                        "behavior" to Schema.enumeration(
                            listOf("CHARGER", "CASTER", "STALKER", "BOSS_WARDEN")
                        ),
                        "maxHp" to Schema.integer("Scaled HP between 50 and 900"),
                        "damage" to Schema.integer("Damage per hit between 10 and 55"),
                        "speed" to Schema.double("Movement speed between 75.0 and 190.0"),
                        "radius" to Schema.double("Size radius between 18.0 and 42.0"),
                        "spawnXRatio" to Schema.double("Normalized X between 0.15 and 0.85"),
                        "spawnYRatio" to Schema.double("Normalized Y between 0.14 and 0.36"),
                        "tintHex" to Schema.string("Monster aura hex e.g. #9E2A3B")
                    )
                ),
                description = "3 to 6 enemies; include one BOSS_WARDEN when depth % 3 == 0"
            )
        )
    )

    private val narrativeSchema = Schema.obj(
        properties = mapOf(
            "chronicleTitle" to Schema.string("Poetic gothic title for this story branch"),
            "speakerName" to Schema.string("Mysterious entity, ghost, or relic voice"),
            "storyText" to Schema.string("2-3 atmospheric sentences presenting a moral/dark fantasy dilemma"),
            "choices" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string("Short snake_case id"),
                        "label" to Schema.string("Action label (max 5 words)"),
                        "consequenceHint" to Schema.string("Short stat/curse preview"),
                        "hpDelta" to Schema.integer("HP change between -30 and +45"),
                        "damageBonusPct" to Schema.integer("Damage % bonus between -10 and +30"),
                        "speedBonusPct" to Schema.integer("Speed % bonus between -10 and +25"),
                        "soulEssenceDelta" to Schema.integer("Soul Essence gain between 0 and 60"),
                        "curseTag" to Schema.string("Short uppercase tag e.g. BLOOD_PACT")
                    )
                ),
                description = "Exactly 3 distinct branching choices"
            )
        )
    )

    private val modReviewSchema = Schema.obj(
        properties = mapOf(
            "loreEn" to Schema.string("Atmospheric English gothic lore (1-2 sentences)"),
            "loreId" to Schema.string("Atmospheric Indonesian gothic lore (1-2 sentences)"),
            "balanceVerdict" to Schema.string("Concise balance & synergy analysis in target language")
        )
    )

    private fun buildHighThinkingConfig(schema: Schema) = generationConfig {
        responseMimeType = "application/json"
        responseSchema = schema
        thinkingConfig = ThinkingConfig.Builder()
            .setThinkingLevel(ThinkingLevel.HIGH)
            .build()
    }

    private val stageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_CARBON,
            generationConfig = buildHighThinkingConfig(stageSchema)
        )
    }

    private val narrativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_CARBON,
            generationConfig = buildHighThinkingConfig(narrativeSchema)
        )
    }

    private val modReviewModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_CARBON,
            generationConfig = buildHighThinkingConfig(modReviewSchema)
        )
    }

    suspend fun generateStage(
        depth: Int,
        archetype: HeroArchetype,
        hpPercent: Int,
        activeMods: List<CommunityMod>,
        pastChoices: List<String>,
        language: AppLanguage,
        isCoop: Boolean
    ): Result<GeneratedStage> {
        val langLabel = if (language == AppLanguage.INDONESIAN) "Indonesian" else "English"
        val modsDesc = if (activeMods.isEmpty()) "None" else activeMods.joinToString {
            "${it.modType}:${it.name}(${it.effectTag}, power=${it.powerValue})"
        }
        val prompt = """
            You are the Gothic Dark Fantasy Dungeon Architect for Nocturne Abyss.
            Generate a unique procedural dungeon chamber for Floor $depth.
            All user-facing strings (title, omenWhisper, enemy names) MUST be in $langLabel.
            Context:
            - Hero Archetype: ${archetype.id}, current HP: $hpPercent%
            - Party mode: ${if (isCoop) "Co-op Raid (increase enemy HP slightly for teamwork)" else "Solo Descent"}
            - Active player mods/mutators: $modsDesc
            - Previous narrative pacts/curses: ${pastChoices.ifEmpty { listOf("None") }.joinToString()}
            - Boss rule: ${if (depth % 3 == 0) "MUST include exactly 1 BOSS_WARDEN enemy with high HP" else "Include 3-5 varied tactical enemies (CHARGER, CASTER, STALKER)"}
            Ensure obstacle coordinates never overlap spawn bottom-center (0.5, 0.82).
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                stageModel.generateContent(prompt)
            }
            val raw = response.text ?: error("AI model returned empty stage blueprint.")
            val json = JSONObject(raw)

            val obstaclesArr = json.getJSONArray("obstacles")
            val obstacles = (0 until obstaclesArr.length()).map { i ->
                val o = obstaclesArr.getJSONObject(i)
                StageObstacle(
                    xRatio = o.getDouble("xRatio").toFloat().coerceIn(0.16f, 0.84f),
                    yRatio = o.getDouble("yRatio").toFloat().coerceIn(0.24f, 0.72f),
                    radius = o.getDouble("radius").toFloat().coerceIn(22f, 44f),
                    kind = o.optString("kind", "PILLAR")
                )
            }

            val enemiesArr = json.getJSONArray("enemies")
            val enemies = (0 until enemiesArr.length()).map { i ->
                val e = enemiesArr.getJSONObject(i)
                val behavior = e.optString("behavior", "CHARGER")
                EnemyBlueprint(
                    id = "enemy_${depth}_$i",
                    name = e.getString("name"),
                    behavior = behavior,
                    maxHp = e.optInt("maxHp", 80).coerceIn(40, 1400),
                    damage = e.optInt("damage", 15).coerceIn(8, 70),
                    speed = e.optDouble("speed", 110.0).toFloat().coerceIn(70f, 200f),
                    radius = e.optDouble("radius", 24.0).toFloat().coerceIn(18f, 46f),
                    spawnXRatio = e.optDouble("spawnXRatio", 0.5).toFloat().coerceIn(0.14f, 0.86f),
                    spawnYRatio = e.optDouble("spawnYRatio", 0.24).toFloat().coerceIn(0.14f, 0.40f),
                    tintHex = e.optString("tintHex", if (behavior == "BOSS_WARDEN") "#D94356" else "#9E2A3B")
                )
            }

            Result.success(
                GeneratedStage(
                    depth = depth,
                    title = json.getString("title"),
                    omenWhisper = json.getString("omenWhisper"),
                    biomeTheme = json.optString("biomeTheme", "CRIMSON_CRYPT"),
                    ambientColorHex = json.optString("ambientColorHex", "#14121A"),
                    torchColorHex = json.optString("torchColorHex", "#D4A359"),
                    fogIntensity = json.optDouble("fogIntensity", 0.45).toFloat().coerceIn(0.25f, 0.65f),
                    obstacles = obstacles,
                    enemies = enemies
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Stage generation timed out", e)
            Result.failure(IllegalStateException("AI stage generation timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stage generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateNarrativeEvent(
        depth: Int,
        archetype: HeroArchetype,
        hpPercent: Int,
        pastChoices: List<String>,
        language: AppLanguage
    ): Result<BranchingNarrativeEvent> {
        val langLabel = if (language == AppLanguage.INDONESIAN) "Indonesian" else "English"
        val prompt = """
            You are the Dark Fantasy Narrative Director for Nocturne Abyss.
            Create a branching gothic shrine encounter after Floor $depth.
            All user-facing text MUST be written in $langLabel with haunting, literary gothic tone.
            Context:
            - Hero: ${archetype.id} at $hpPercent% HP
            - Past pacts/choices: ${pastChoices.ifEmpty { listOf("First descent") }.joinToString()}
            Provide 3 distinct meaningful choices with trade-offs (e.g. sacrifice HP for power, shatter relic for Soul Essence, or seek quiet healing).
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                narrativeModel.generateContent(prompt)
            }
            val raw = response.text ?: error("AI model returned empty narrative.")
            val json = JSONObject(raw)
            val choicesArr = json.getJSONArray("choices")
            val choices = (0 until choicesArr.length()).map { i ->
                val c = choicesArr.getJSONObject(i)
                NarrativeChoice(
                    id = c.optString("id", "choice_$i"),
                    label = c.getString("label"),
                    consequenceHint = c.getString("consequenceHint"),
                    hpDelta = c.optInt("hpDelta", 0).coerceIn(-35, 50),
                    damageBonusPct = c.optInt("damageBonusPct", 0).coerceIn(-10, 35),
                    speedBonusPct = c.optInt("speedBonusPct", 0).coerceIn(-10, 30),
                    soulEssenceDelta = c.optInt("soulEssenceDelta", 10).coerceIn(0, 75),
                    curseTag = c.optString("curseTag", "OMEN")
                )
            }
            Result.success(
                BranchingNarrativeEvent(
                    chronicleTitle = json.getString("chronicleTitle"),
                    speakerName = json.getString("speakerName"),
                    storyText = json.getString("storyText"),
                    choices = choices
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Narrative generation timed out", e)
            Result.failure(IllegalStateException("AI narrative timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Narrative generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun reviewModBalanceAndLore(
        modType: String,
        name: String,
        effectTag: String,
        powerValue: Int,
        speedValue: Int,
        language: AppLanguage
    ): Result<ModAiReview> {
        val langLabel = if (language == AppLanguage.INDONESIAN) "Indonesian" else "English"
        val prompt = """
            Review and enrich this player-created dark fantasy mod for Nocturne Abyss:
            - Type: $modType
            - Name: $name
            - Effect Tag: $effectTag
            - Power Value: $powerValue (1..500)
            - Speed/Tempo Value: $speedValue (1..300)
            Write evocative gothic lore in both English (loreEn) and Indonesian (loreId), and write balanceVerdict in $langLabel.
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                modReviewModel.generateContent(prompt)
            }
            val raw = response.text ?: error("AI model returned empty mod review.")
            val json = JSONObject(raw)
            Result.success(
                ModAiReview(
                    loreEn = json.getString("loreEn"),
                    loreId = json.getString("loreId"),
                    balanceVerdict = json.getString("balanceVerdict")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Mod review timed out", e)
            Result.failure(IllegalStateException("AI mod review timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Mod review failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "DungeonAiRepo"
        const val MODEL_CARBON = "gemini-4-carbon"
        const val AI_TIMEOUT_MS = 60_000L
    }
}
