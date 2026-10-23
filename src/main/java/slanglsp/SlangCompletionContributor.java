package slanglsp;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Basic (Ctrl+Space) completion for Slang.
 * <p>
 * LSP4IJ already contributes {@code slangd}'s semantic completions. This contributor adds the
 * language's static vocabulary (keywords, builtin types and functions) plus the symbols declared in
 * the current file, so the completion list is useful even when the server has nothing to suggest at
 * the caret. CompletionResultSet deduplicates lookup strings, so overlapping server items are fine.
 */
public class SlangCompletionContributor extends CompletionContributor {

    private static final List<String> BUILTIN_TYPES = List.of(
            "bool", "int", "uint", "int8_t", "int16_t", "int32_t", "int64_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t",
            "half", "float", "double", "float16_t", "float32_t", "float64_t",
            "int2", "int3", "int4", "uint2", "uint3", "uint4",
            "float2", "float3", "float4", "double2", "double3", "double4",
            "float2x2", "float2x3", "float2x4", "float3x2", "float3x3", "float3x4",
            "float4x2", "float4x3", "float4x4", "int2x2", "int3x3", "int4x4",
            "vector", "matrix", "Array", "Texture1D", "Texture2D", "Texture3D", "TextureCube",
            "SamplerState", "SamplerComparisonState", "ConstantBuffer", "StructuredBuffer",
            "RWStructuredBuffer", "ByteAddressBuffer", "RaytracingAccelerationStructure"
    );

    private static final List<String> BUILTIN_FUNCTIONS = List.of(
            "abs", "acos", "all", "any", "asin", "atan", "atan2", "ceil", "clamp",
            "cos", "cosh", "cross", "degrees", "determinant", "distance", "dot", "exp", "exp2",
            "faceforward", "floor", "fmod", "frac", "length", "lerp", "log", "log2",
            "max", "min", "modf", "mul", "normalize", "pow", "radians", "reflect", "refract",
            "round", "rsqrt", "saturate", "sign", "sin", "sinh", "smoothstep", "sqrt", "step",
            "tan", "tanh", "transpose", "trunc", "asfloat", "asint", "asuint", "clip", "ddx",
            "ddy", "fwidth", "sincos", "printf", "select"
    );

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        if (parameters.getCompletionType() != CompletionType.BASIC) {
            return;
        }
        PsiFile file = parameters.getOriginalFile();
        if (!(file instanceof SlangFile)) {
            return;
        }

        String text = file.getText();
        int offset = parameters.getOffset();
        if (isInsideCommentOrString(text, offset) || isMemberAccess(text, offset)) {
            return;
        }

        Set<String> added = new HashSet<>();
        for (String keyword : SlangLexer.keywords()) {
            add(result, added, keyword, null, "keyword", -1.0);
        }
        for (String type : BUILTIN_TYPES) {
            add(result, added, type, AllIcons.Nodes.Class, "type", -0.5);
        }
        for (String function : BUILTIN_FUNCTIONS) {
            add(result, added, function, AllIcons.Nodes.Function, "function", -0.5);
        }

        // Symbols declared in the current file, so at least same-file completion works without
        // waiting for the server.
        SlangLexer lexer = new SlangLexer();
        lexer.start(text);
        while (lexer.getTokenType() != null) {
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            if (SlangOccurrences.isIdentifierToken(lexer.getTokenType())
                    && (offset < start || offset > end)) {
                String name = text.subSequence(start, end).toString();
                var icon = lexer.getTokenType() == SlangLexer.TYPE
                        ? AllIcons.Nodes.Class
                        : lexer.getTokenType() == SlangLexer.FUNCTION
                        ? AllIcons.Nodes.Function
                        : AllIcons.Nodes.Variable;
                add(result, added, name, icon, null, 0.0);
            }
            lexer.advance();
        }
    }

    private static boolean isInsideCommentOrString(String text, int offset) {
        int probe = Math.max(0, Math.min(offset, text.length()) - 1);
        IElementType type = SlangOccurrences.tokenTypeAt(text, probe);
        return type == SlangLexer.COMMENT || type == SlangLexer.STRING;
    }

    /**
     * After a dot the server's member completion is the only sensible source; keywords/types would
     * just be noise.
     */
    private static boolean isMemberAccess(String text, int offset) {
        int i = Math.min(offset, text.length()) - 1;
        // Skip the identifier that is being completed.
        while (i >= 0 && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
            i--;
        }
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        return i >= 0 && text.charAt(i) == '.';
    }

    private static void add(CompletionResultSet result,
                            Set<String> added,
                            String name,
                            javax.swing.Icon icon,
                            String typeText,
                            double priority) {
        if (!added.add(name)) {
            return;
        }
        LookupElementBuilder element = LookupElementBuilder.create(name);
        if (icon != null) {
            element = element.withIcon(icon);
        }
        if (typeText != null) {
            element = element.withTypeText(typeText);
        }
        result.addElement(PrioritizedLookupElement.withPriority(element, priority));
    }
}
