package me.zeroeightsix.kami.setting

import com.mojang.authlib.GameProfile
import imgui.ImGui.colorEdit4
import imgui.ImGui.dragScalar
import imgui.ImGui.inputText
import imgui.flag.ImGuiColorEditFlags
import imgui.flag.ImGuiDataType
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.AnnotatedSettings
import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.processor.ParameterizedTypeProcessor
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.BooleanSerializableType.BOOLEAN
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.RecordSerializableType
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.StringSerializableType.DEFAULT_STRING
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.ConfigType
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.ConfigTypes
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.EnumConfigType
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.NumberConfigType
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.RecordConfigType
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.StringConfigType
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.FiberSerialization
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.JanksonValueSerializer
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigBranch
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigLeaf
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigNode
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree
import java.math.BigDecimal
import java.nio.file.Paths
import java.util.UUID
import java.util.stream.Stream
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.io.path.inputStream
import kotlin.math.floor
import kotlin.math.log10
import me.zeroeightsix.kami.Colour
import me.zeroeightsix.kami.feature.FeatureManager
import me.zeroeightsix.kami.feature.hidden.ClickGui
import me.zeroeightsix.kami.feature.module.Module
import me.zeroeightsix.kami.gui.ImguiDSL.checkbox
import me.zeroeightsix.kami.gui.ImguiDSL.combo
import me.zeroeightsix.kami.gui.ImguiDSL.imgui
import me.zeroeightsix.kami.gui.ImguiDSL.wrapImFloat
import me.zeroeightsix.kami.gui.View
import me.zeroeightsix.kami.gui.bindButton
import me.zeroeightsix.kami.gui.text.CompiledText
import me.zeroeightsix.kami.gui.text.VarMap
import me.zeroeightsix.kami.gui.widgets.PinnableWidget
import me.zeroeightsix.kami.gui.widgets.TextPinnableWidget
import me.zeroeightsix.kami.gui.windows.Settings
import me.zeroeightsix.kami.gui.windows.modules.Modules
import me.zeroeightsix.kami.gui.wizard.Wizard
import me.zeroeightsix.kami.mixin.extend.getMap
import me.zeroeightsix.kami.splitFirst
import me.zeroeightsix.kami.target.BlockCategory
import me.zeroeightsix.kami.target.BlockEntityCategory
import me.zeroeightsix.kami.target.BlockEntitySupplier
import me.zeroeightsix.kami.target.BlockSupplier
import me.zeroeightsix.kami.target.EntityCategory
import me.zeroeightsix.kami.target.EntitySupplier
import me.zeroeightsix.kami.target.ItemCategory
import me.zeroeightsix.kami.target.ItemSupplier
import me.zeroeightsix.kami.target.createTargetsType
import me.zeroeightsix.kami.then
import me.zeroeightsix.kami.unsignedInt
import me.zeroeightsix.kami.util.Bind
import net.minecraft.client.util.InputUtil
import net.minecraft.command.CommandSource
import net.minecraft.util.Identifier

object KamiConfig : FiberController by FiberControllerImpl(Paths.get("kami"), JanksonValueSerializer(false)) {

    /** Config types **/

    val unitType = RecordConfigType(RecordSerializableType(mapOf()), Unit::class.java, { }, { mapOf() })

    val mutableListTypeProcessor = ParameterizedTypeProcessor {
        fun <T> makeMutableListType(type: ConfigType<T, *, *>) =
            ConfigTypes.makeList(type).derive(
                ArrayList::class.java,
                {
                    ArrayList(it)
                },
                {
                    it
                }
            )
        makeMutableListType(it[0])
    }

    val identifierType = ConfigTypes.STRING.derive(
        Identifier::class.java,
        {
            Identifier(it)
        },
        {
            it.toString()
        }
    )

    // This should be done with an enumconfigtype but unfortunately map types only accept string types as keys,
    // maybe should make an issue for this on the fiber repo
    val entityCategoryType = ConfigTypes.STRING.derive(
        EntityCategory::class.java,
        {
            EntityCategory.valueOf(it)
        },
        {
            it.name
        }
    )
    val entitySpecificType = identifierType.derive(
        EntitySupplier.SpecificEntity::class.java,
        {
            EntitySupplier.SpecificEntity(it)
        },
        {
            it.typeIdentifier
        }
    )

    val blockEntityCategoryType = ConfigTypes.STRING.derive(
        BlockEntityCategory::class.java,
        {
            BlockEntityCategory.valueOf(it)
        },
        {
            it.name
        }
    )
    val blockEntitySpecificType = identifierType.derive(
        BlockEntitySupplier.SpecificBlockEntity::class.java,
        {
            BlockEntitySupplier.SpecificBlockEntity(it)
        },
        {
            it.typeIdentifier
        }
    )

    val blockCategoryType = ConfigTypes.STRING.derive(
        BlockCategory::class.java,
        {
            BlockCategory.valueOf(it)
        },
        {
            it.name
        }
    )
    val blockSpecificType = identifierType.derive(
        BlockSupplier.SpecificBlock::class.java,
        {
            BlockSupplier.SpecificBlock(it)
        },
        {
            it.typeIdentifier
        }
    )

    val itemCategoryType = ConfigTypes.STRING.derive(
        ItemCategory::class.java,
        {
            ItemCategory.valueOf(it)
        },
        {
            it.name
        }
    )
    val itemSpecificType = identifierType.derive(
        ItemSupplier.SpecificItem::class.java,
        {
            ItemSupplier.SpecificItem(it)
        },
        {
            it.typeIdentifier
        }
    )

    fun <M, S> createEntityTargetsType(metaType: ConfigType<M, S, *>) = createTargetsType(
        metaType,
        entityCategoryType,
        entitySpecificType,
        { EntitySupplier.SpecificEntity() }
    ) { e, s ->
        EntitySupplier(e, s)
    }

    fun <M, S> createBlockTargetsType(metaType: ConfigType<M, S, *>) = createTargetsType(
        metaType,
        blockEntityCategoryType,
        blockEntitySpecificType,
        { BlockEntitySupplier.SpecificBlockEntity() }
    ) { e, s ->
        BlockEntitySupplier(e, s)
    }

    fun <M, S> createBlockType(metaType: ConfigType<M, S, *>) =
        createTargetsType(metaType, blockCategoryType, blockSpecificType, { BlockSupplier.SpecificBlock() }) { e, s ->
            BlockSupplier(e, s)
        }

    fun <M, S> createItemType(metaType: ConfigType<M, S, *>) =
        createTargetsType(metaType, itemCategoryType, itemSpecificType, { ItemSupplier.SpecificItem() }) { e, s ->
            ItemSupplier(e, s)
        }

    val entityTargetsTypeProcessor = ParameterizedTypeProcessor<EntitySupplier<*>> {
        createEntityTargetsType(it[0])
    }

    val blockEntityTargetsTypeProcessor = ParameterizedTypeProcessor<BlockEntitySupplier<*>> {
        createBlockTargetsType(it[0])
    }

    val blockTargetsTypeProcessor = ParameterizedTypeProcessor<BlockSupplier<*>> {
        createBlockType(it[0])
    }

    val itemTargetsTypeProcessor = ParameterizedTypeProcessor<ItemSupplier<*>> {
        createItemType(it[0])
    }

    val colourType =
        ConfigTypes.STRING
            .derive(
                Colour::class.java,
                {
                    Colour.fromARGB((it.toLongOrNull(radix = 16) ?: 0xFFFFFFFF).unsignedInt)
                },
                {
                    Integer.toHexString(it.asARGB())
                }
            )
            .extend(
                {
                    Integer.toHexString(it.asARGB())
                },
                {
                    Colour.fromARGB(it.toInt(radix = 16))
                },
                { name, colour ->
                    val floats = colour.asFloatRGBA()
                    colorEdit4(
                        name,
                        floats,
                        ImGuiColorEditFlags.AlphaBar or ImGuiColorEditFlags.NoInputs
                    ) then {
                        Colour.fromFloatRGBA(floats)
                    }
                }
            )

    val colourModeType = ConfigTypes.makeEnum(CompiledText.Part.ColourMode::class.java)
    val partSerializableType = RecordSerializableType(
        mapOf(
            "obfuscated" to BOOLEAN,
            "bold" to BOOLEAN,
            "strike" to BOOLEAN,
            "underline" to BOOLEAN,
            "italic" to BOOLEAN,
            "shadow" to BOOLEAN,
            "extraspace" to BOOLEAN,
            "colourMode" to colourModeType.serializedType,
            "type" to DEFAULT_STRING,
            "value" to DEFAULT_STRING,
            "colour" to colourType.serializedType,
            "digits" to ConfigTypes.INTEGER.serializedType
        )
    )
    val variableType = ConfigTypes.STRING.derive(
        CompiledText.Variable::class.java,
        {
            (VarMap[it] ?: VarMap["none"]!!)()
        },
        {
            it.name
        }
    )
    val numericalVariableType = variableType.derive(
        CompiledText.NumericalVariable::class.java,
        {
            it as CompiledText.NumericalVariable
        },
        {
            it
        }
    )
    val partType = RecordConfigType(
        partSerializableType,
        CompiledText.Part::class.java,
        { it ->
            val obfuscated = it["obfuscated"] as Boolean
            val bold = it["bold"] as Boolean
            val strike = it["strike"] as Boolean
            val underline = it["underline"] as Boolean
            val italic = it["italic"] as Boolean
            val shadow = it["shadow"] as Boolean
            val extraSpace = it["extraspace"] as Boolean
            val colourMode = colourModeType.toRuntimeType(it["colourMode"] as String)
            val type = it["type"] as String
            val value = it["value"] as String
            val colour = colourType.toRuntimeType(it["colour"] as String)
            val digits = ConfigTypes.INTEGER.toRuntimeType(it["digits"] as BigDecimal?)

            val part = when (type) {
                "literal" -> CompiledText.LiteralPart(
                    value.imgui,
                    obfuscated,
                    bold,
                    strike,
                    underline,
                    italic,
                    shadow,
                    colourMode,
                    extraSpace
                )
                "variable" -> CompiledText.VariablePart(
                    variableType.toRuntimeType(value)
                        .also { v -> (v as? CompiledText.NumericalVariable)?.digits = digits },
                    obfuscated,
                    bold,
                    strike,
                    underline,
                    italic,
                    shadow,
                    colourMode,
                    extraSpace
                )
                else -> CompiledText.LiteralPart(
                    "Invalid part".imgui,
                    obfuscated,
                    bold,
                    strike,
                    underline,
                    italic,
                    shadow,
                    colourMode,
                    extraSpace
                )
            }
            part.colour = colour
            part
        },
        {
            val (type, value) = when (it) {
                is CompiledText.LiteralPart -> "literal" to it.string.get()
                is CompiledText.VariablePart -> "variable" to variableType.toSerializedType(it.variable)
                else -> throw IllegalStateException("Unknown part type")
            }
            mapOf(
                "obfuscated" to it.obfuscated,
                "bold" to it.bold,
                "strike" to it.strike,
                "underline" to it.underline,
                "italic" to it.italic,
                "shadow" to it.shadow,
                "extraspace" to it.extraSpace,
                "colourMode" to colourModeType.toSerializedType(it.colourMode),
                "type" to type,
                "value" to value,
                "colour" to colourType.toSerializedType(it.colour),
                "digits" to ConfigTypes.INTEGER.toSerializedType(
                    ((it as? CompiledText.VariablePart)?.variable as? CompiledText.NumericalVariable)?.digits ?: 0
                )
            )
        }
    )
    val compiledTextType = ConfigTypes.makeList(partType).derive(
        CompiledText::class.java,
        {
            CompiledText(it.toMutableList())
        },
        {
            it.parts
        }
    ).extend(
        {
            it.toString()
        },
        {
            throw InvalidValueException("CompiledTexts can not be made from text")
        },
        { name, text ->
            text.edit(
                name,
                false,
                selectedAction = { part ->
                    part.editValue(VarMap.inner)
                }
            ) then { text }
        }
    )
    val listOfCompiledTextType = ConfigTypes.makeList(compiledTextType)
    val positionType = ConfigTypes.makeEnum(PinnableWidget.Position::class.java)
    val alignmentType = ConfigTypes.makeEnum(TextPinnableWidget.Alignment::class.java)
    val orderingType = ConfigTypes.makeEnum(TextPinnableWidget.Ordering::class.java)
    val textPinnableSerializableType = RecordSerializableType(
        mapOf(
            "texts" to listOfCompiledTextType.serializedType,
            "title" to DEFAULT_STRING,
            "position" to positionType.serializedType,
            "alignment" to alignmentType.serializedType,
            "ordering" to orderingType.serializedType
        )
    )
    val textPinnableWidgetType = RecordConfigType(
        textPinnableSerializableType,
        TextPinnableWidget::class.java,
        {
            val title = it["title"] as String
            val position = positionType.toRuntimeType(it["position"] as String?)
            @Suppress("UNCHECKED_CAST") val texts =
                listOfCompiledTextType.toRuntimeType(it["texts"] as List<List<Map<String, Any>>>?)
            val alignment = alignmentType.toRuntimeType(it["alignment"] as String?)
            val ordering = orderingType.toRuntimeType(it["ordering"] as String?)
            TextPinnableWidget(title, texts.toMutableList(), position, alignment, ordering)
        },
        {
            mapOf(
                "texts" to listOfCompiledTextType.toSerializedType(it.text),
                "title" to it.title,
                "position" to positionType.toSerializedType(it.position),
                "alignment" to alignmentType.toSerializedType(it.alignment),
                "ordering" to orderingType.toSerializedType(it.ordering)
            )
        }
    )
    val moduleType = ConfigTypes.STRING.derive<Module>(
        Module::class.java,
        { name ->
            FeatureManager.modules.find { it.name == name }
        },
        { m ->
            m.name
        }
    )
    val moduleListType = ConfigTypes.makeList(moduleType)
    val modulesGroupsType = ConfigTypes.makeMap(ConfigTypes.STRING, moduleListType)
    val windowsType = ConfigTypes.makeMap(ConfigTypes.STRING, modulesGroupsType)
        .derive(
            Modules.Windows::class.java,
            {
                val windows = mutableListOf<Modules.ModuleWindow>()
                for ((nameAndId, groups) in it) {
                    val (id, name) = nameAndId.splitFirst('-')
                    windows.add(
                        Modules.ModuleWindow(
                            name,
                            groups = groups.mapValues { it.value.toMutableList() }.toMutableMap(),
                            id = id.toInt()
                        )
                    )
                }
                Modules.Windows(windows)
            },
            {
                val map = mutableMapOf<String, Map<String, List<Module>>>()
                for (window in it) {
                    val nameAndId = "${window.id}-${window.title}"
                    map[nameAndId] = window.groups.toMutableMap()
                }
                map
            }
        )

    val bindType = ConfigTypes.STRING
        .derive(
            Bind::class.java,
            {
                var s = it.toLowerCase()

                fun remove(part: String): Boolean {
                    val removed = s.replace(part, "")
                    val changed = s != removed
                    s = removed
                    return changed
                }

                val ctrl = remove("ctrl+")
                val alt = remove("alt+")
                val shift = remove("shift+")

                s = s.removePrefix("key.keyboard.")

                Bind(
                    ctrl,
                    alt,
                    shift,
                    bindMap[s] ?: Bind.Code.none()
                )
            },
            {
                var s = ""
                if (it.isCtrl) s += "ctrl+"
                if (it.isAlt) s += "alt+"
                if (it.isShift) s += "shift+"
                s += it.code.translationKey
                s
            }
        )
        .extend(
            { name, bind ->
                return@extend bindButton(name, bind)
            },
            { _, b ->
                val range = 0..b.remaining.lastIndexOf('+')
                val left = b.remaining.substring(range)
                val right = b.remaining.toLowerCase().removeRange(range)
                Stream.concat(
                    listOf("ctrl+", "shift+", "alt+").stream(),
                    bindMap.keys.stream()
                ).forEach {
                    if (it.startsWith(right)) b.suggest("$left$it")
                }
                b.buildFuture()
            }
        )

    val profileType =
        ConfigTypes.makeMap(ConfigTypes.STRING, ConfigTypes.STRING)
            .derive(
                GameProfile::class.java,
                { map: Map<String?, String?> ->
                    GameProfile(
                        UUID.fromString(map["uuid"]),
                        map["name"]
                    )
                }
            ) { profile: GameProfile ->
                mapOf(
                    "uuid" to profile.id.toString(),
                    "name" to profile.name
                )
            }
    val friendsType =
        ConfigTypes.makeList(profileType)

    private val bindMap = (InputUtil.Type.KEYSYM.getMap()).mapNotNull {
        val name = it.value.translationKey.also { s ->
            if (!s.startsWith("key.keyboard")) return@mapNotNull null
        }.removePrefix("key.keyboard.").replace('.', '-')

        Pair(name, Bind.Code(it.value!!))
    }.toMap()

    /** Extending base types **/

    init {
        ConfigTypes.BOOLEAN.extend(
            {
                it.toString()
            },
            {
                it.toBoolean()
            },
            { name, bool ->
                val value = ImBoolean(bool)
                checkbox(name, value) {
                    return@extend value.get()
                }
                null
            },
            { _, b -> CommandSource.suggestMatching(listOf("true", "false"), b) }
        )
    }

    private fun installBaseExtensions(node: ConfigNode) {
        when (node) {
            is ConfigBranch ->
                node.items.forEach { installBaseExtensions(it) }
            is ConfigLeaf<*> -> {
                installBaseExtension(node)
            }
        }
    }

    private fun <T : ConfigLeaf<*>> installBaseExtension(node: T) {
        val type = node.getAnyRuntimeConfigType()

        installBaseExtension(type)
    }

    private val settings = AnnotatedSettings.builder()
        .collectMembersRecursively()
        .collectOnlyAnnotatedMembers()
        .useNamingConvention(ProperCaseConvention)
        .registerTypeMapping(ArrayList::class.java, mutableListTypeProcessor)
        .registerTypeMapping(EntitySupplier::class.java, entityTargetsTypeProcessor)
        .registerTypeMapping(BlockEntitySupplier::class.java, blockEntityTargetsTypeProcessor)
        .registerTypeMapping(BlockSupplier::class.java, blockTargetsTypeProcessor)
        .registerTypeMapping(ItemSupplier::class.java, itemTargetsTypeProcessor)
        .registerTypeMapping(Bind::class.java, bindType)
        .registerTypeMapping(GameProfile::class.java, profileType)
        .registerTypeMapping(Colour::class.java, colourType)
        .registerTypeMapping(Modules.Windows::class.java, windowsType)
        .registerTypeMapping(TextPinnableWidget::class.java, textPinnableWidgetType)
        .registerTypeMapping(CompiledText::class.java, compiledTextType)
        .registerTypeMapping(CompiledText.NumericalVariable::class.java, numericalVariableType)
        .registerTypeMapping(TextPinnableWidget.Alignment::class.java, alignmentType)
        .registerTypeMapping(Unit::class.java, unitType)
        .registerSettingProcessor(SettingVisibility.Constant::class.java, ConstantVisibilityAnnotationProcessor)
        .registerSettingProcessor(SettingVisibility.Method::class.java, MethodVisibilityAnnotationProcessor)
        .registerSettingProcessor(ImGuiExtra.Pre::class.java, ImGuiExtraPreAnnotationProcessor)
        .registerSettingProcessor(ImGuiExtra.Post::class.java, ImGuiExtraPostAnnotationProcessor)
        .build()

    var float: Float = 0f

    /**
     * Sets `type`'s [SettingInterface] to an applicable generic setting interface, if available.
     *
     * Ignores types that already have a setting interface attached.
     */
    fun installBaseExtension(type: ConfigType<Any, out Any, *>?) {
        if (type == null || type.settingInterface != null) return
        when (type) {
            // NumberConfigTypes use BigDecimal: we can assume that we can just pass a BigDecimal to this leaf and it'll take it
            is NumberConfigType<*> -> {
                @Suppress("NAME_SHADOWING") val type = (type as NumberConfigType<Any>)
                type.extend(
                    {
                        it.toString()
                    },
                    {
                        type.toRuntimeType(BigDecimal(it))
                    },
                    { name, value ->
                        val sType = type.serializedType
                        this.float = type.toSerializedType(value).toFloat()
                        val increment = sType.increment?.toFloat() ?: 1.0f
                        val precision = floor(log10(1f / increment)).toInt()
                        val format = "%.${precision}f"
                        wrapImFloat(::float) {
                            dragScalar(
                                name,
                                ImGuiDataType.Float,
                                it,
                                increment,
                                sType.minimum?.toFloat() ?: 0f,
                                sType.maximum?.toFloat() ?: 0f,
                                format
                            )
                        }.then {
                            // If the value changed, return it
                            // of course we also need to correct the type again
                            type.toRuntimeType(BigDecimal.valueOf(float.toDouble()))
                        } // If it didn't change, this will return null
                    },
                    type = "number"
                )
            }
            is EnumConfigType<*> -> {
                @Suppress("NAME_SHADOWING") val type = (type as EnumConfigType<Any>)
                val values = type.serializedType.validValues.toList()
                type.extend(
                    {
                        it.toString()
                    },
                    {
                        type.toRuntimeType(it)
                    },
                    { name, value ->
                        val index = ImInt(values.indexOf(type.toSerializedType(value)))
                        combo(name, index, values) {
                            return@extend type.toRuntimeType(values[index.get()])
                        }
                        null
                    },
                    { _, b ->
                        CommandSource.suggestMatching(values, b)
                    },
                    type = "enum"
                )
            }
            is StringConfigType<*> -> {
                @Suppress("NAME_SHADOWING") val type = (type as StringConfigType<Any>)
                type.extend(
                    {
                        it.toString()
                    },
                    {
                        it
                    },
                    { name, value ->
                        val buf = ImString(type.toSerializedType(value))
                        if (inputText(name, buf)) buf.get() else null
                    }
                )
            }
        }
    }

    fun register(serviceName: String, pojo: Any, loadNow: Boolean = true): ConfigBranch {
        val tree = ConfigTree.builder().applyFromPojo(pojo, this.settings).build()
        installBaseExtensions(tree)
        this.register(serviceName, tree, loadNow)
        return tree
    }

    fun loadFromMonolithicConfig() {
        val tree = ConfigTree.builder()
            .fork("features").run {
                FeatureManager.fullFeatures.forEach {
                    fork(it.name).applyFromPojo(it, settings).finishBranch()
                }
                this
            }.finishBranch()
            .fork("Windows").applyFromPojo(Modules, settings).finishBranch()
            .applyFromPojo(Settings, settings)
            .applyFromPojo(Wizard, settings)
            .fork("view").applyFromPojo(View, settings).finishBranch()
            .fork("clickGui").applyFromPojo(ClickGui, settings).finishBranch()
            .build()

        FiberSerialization.deserialize(
            tree,
            Paths.get("KAMI_config.json5").inputStream(),
            JanksonValueSerializer(false)
        )
    }
}