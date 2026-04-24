package com.xbk.lattice.compiler.ast.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.compiler.ast.domain.AstEntityType;
import com.xbk.lattice.compiler.ast.domain.AstExtractionResult;
import com.xbk.lattice.compiler.ast.domain.AstFact;
import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.ast.domain.AstRelation;
import com.xbk.lattice.compiler.ast.domain.AstSourceFile;
import com.xbk.lattice.infra.persistence.GraphEntityJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphFactJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphRelationJdbcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * AST 图谱抽取服务
 *
 * 职责：解析 Java 源文件并将实体、事实、关系落入图谱表
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class AstGraphExtractService {

    private final GraphEntityJdbcRepository graphEntityJdbcRepository;

    private final GraphFactJdbcRepository graphFactJdbcRepository;

    private final GraphRelationJdbcRepository graphRelationJdbcRepository;

    private final TransactionTemplate transactionTemplate;

    /**
     * 创建 AST 图谱抽取服务。
     *
     * @param graphEntityJdbcRepository 实体仓储
     * @param graphFactJdbcRepository 事实仓储
     * @param graphRelationJdbcRepository 关系仓储
     * @param platformTransactionManager 事务管理器
     */
    public AstGraphExtractService(
            GraphEntityJdbcRepository graphEntityJdbcRepository,
            GraphFactJdbcRepository graphFactJdbcRepository,
            GraphRelationJdbcRepository graphRelationJdbcRepository,
            PlatformTransactionManager platformTransactionManager
    ) {
        this.graphEntityJdbcRepository = graphEntityJdbcRepository;
        this.graphFactJdbcRepository = graphFactJdbcRepository;
        this.graphRelationJdbcRepository = graphRelationJdbcRepository;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    }

    /**
     * 抽取并落库 AST 图谱。
     *
     * @param sourceDir 源目录
     * @param sourceFiles 源文件列表
     * @return 抽取报告
     */
    public AstGraphExtractReport extract(String sourceDir, List<AstSourceFile> sourceFiles) {
        AstGraphExtractReport report = new AstGraphExtractReport();
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return report;
        }
        ParserConfiguration parserConfiguration = buildParserConfiguration(sourceDir);
        JavaParser javaParser = new JavaParser(parserConfiguration);
        for (AstSourceFile sourceFile : sourceFiles) {
            if (sourceFile == null || !sourceFile.getRelativePath().endsWith(".java")) {
                continue;
            }
            try {
                AstExtractionResult extractionResult = parseSingleFile(javaParser, sourceFile);
                transactionTemplate.executeWithoutResult(status -> persistSingleFile(sourceFile, extractionResult, report));
                report.getWarnings().addAll(extractionResult.warnings());
            }
            catch (RuntimeException exception) {
                report.getWarnings().add(sourceFile.getRelativePath() + ":" + exception.getMessage());
            }
        }
        return report;
    }

    private ParserConfiguration buildParserConfiguration(String sourceDir) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        if (sourceDir != null && !sourceDir.isBlank()) {
            Path sourcePath = Path.of(sourceDir);
            if (Files.exists(sourcePath)) {
                typeSolver.add(new JavaParserTypeSolver(sourcePath));
            }
        }
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        return parserConfiguration;
    }

    private AstExtractionResult parseSingleFile(JavaParser javaParser, AstSourceFile sourceFile) {
        AstExtractionResult extractionResult = AstExtractionResult.empty();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceFile.getContent());
        Optional<CompilationUnit> compilationUnitOptional = parseResult.getResult();
        if (compilationUnitOptional.isEmpty()) {
            extractionResult.addWarning(sourceFile.getRelativePath() + ":parse_failed");
            return extractionResult;
        }
        CompilationUnit compilationUnit = compilationUnitOptional.orElseThrow();
        String packageName = compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString())
                .orElse("");
        for (TypeDeclaration<?> typeDeclaration : compilationUnit.getTypes()) {
            extractType(sourceFile, packageName, typeDeclaration, extractionResult);
        }
        return extractionResult;
    }

    private void extractType(
            AstSourceFile sourceFile,
            String packageName,
            TypeDeclaration<?> typeDeclaration,
            AstExtractionResult extractionResult
    ) {
        AstEntity entity = new AstEntity();
        entity.setId(sourceFile.getRelativePath() + "#" + typeDeclaration.getNameAsString());
        entity.setCanonicalName(packageName.isBlank()
                ? typeDeclaration.getNameAsString()
                : packageName + "." + typeDeclaration.getNameAsString());
        entity.setSimpleName(typeDeclaration.getNameAsString());
        entity.setEntityType(resolveEntityType(typeDeclaration));
        entity.setSystemLabel(sourceFile.getSystemLabel());
        entity.setSourceFileId(sourceFile.getSourceFileId());
        entity.setAnchorRef(sourceFile.getRelativePath() + ":" + beginLine(typeDeclaration));
        entity.setResolutionStatus("RESOLVED");
        entity.setMetadataJson("{\"package\":\"" + packageName + "\"}");
        extractionResult.addEntity(entity);

        extractionResult.addFact(buildFact(entity.getId(), "package", packageName, sourceFile, typeDeclaration, "class_extractor", 1.0D));
        for (AnnotationExpr annotationExpr : typeDeclaration.getAnnotations()) {
            extractionResult.addFact(buildFact(
                    entity.getId(),
                    "annotation",
                    annotationExpr.getNameAsString(),
                    sourceFile,
                    annotationExpr,
                    "annotation_extractor",
                    0.9D
            ));
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classDeclaration) {
            appendTypeRelations(entity.getId(), classDeclaration.getExtendedTypes(), "extends", sourceFile, extractionResult, 0.9D);
            appendTypeRelations(entity.getId(), classDeclaration.getImplementedTypes(), "implements", sourceFile, extractionResult, 0.9D);
        }
        for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {
            AstEntity methodEntity = new AstEntity();
            String methodId = entity.getId() + "#" + methodDeclaration.getNameAsString() + "/" + methodDeclaration.getParameters().size();
            methodEntity.setId(methodId);
            methodEntity.setCanonicalName(entity.getCanonicalName() + "." + methodDeclaration.getNameAsString());
            methodEntity.setSimpleName(methodDeclaration.getNameAsString());
            methodEntity.setEntityType(AstEntityType.METHOD);
            methodEntity.setSystemLabel(sourceFile.getSystemLabel());
            methodEntity.setSourceFileId(sourceFile.getSourceFileId());
            methodEntity.setAnchorRef(sourceFile.getRelativePath() + ":" + beginLine(methodDeclaration));
            methodEntity.setResolutionStatus("RESOLVED");
            methodEntity.setMetadataJson("{\"returnType\":\"" + methodDeclaration.getTypeAsString() + "\"}");
            extractionResult.addEntity(methodEntity);
            extractionResult.addRelation(buildRelation(entity.getId(), "declares_method", methodId, sourceFile, methodDeclaration, 1.0D, "method_extractor"));
            extractionResult.addFact(buildFact(
                    methodId,
                    "signature",
                    methodDeclaration.getDeclarationAsString(false, false, false),
                    sourceFile,
                    methodDeclaration,
                    "method_extractor",
                    1.0D
            ));
            appendEndpointFacts(methodId, sourceFile, methodDeclaration, extractionResult);
            appendMethodCalls(methodId, sourceFile, methodDeclaration, extractionResult);
        }
    }

    private void appendTypeRelations(
            String srcId,
            NodeList<?> nodeList,
            String edgeType,
            AstSourceFile sourceFile,
            AstExtractionResult extractionResult,
            double confidence
    ) {
        for (Object item : nodeList) {
            String target = String.valueOf(item);
            extractionResult.addRelation(buildRelation(
                    srcId,
                    edgeType,
                    target,
                    sourceFile,
                    null,
                    confidence,
                    "type_relation_extractor"
            ));
        }
    }

    private void appendEndpointFacts(
            String methodId,
            AstSourceFile sourceFile,
            MethodDeclaration methodDeclaration,
            AstExtractionResult extractionResult
    ) {
        for (AnnotationExpr annotationExpr : methodDeclaration.getAnnotations()) {
            String annotationName = annotationExpr.getNameAsString();
            if (!annotationName.endsWith("Mapping")) {
                continue;
            }
            extractionResult.addFact(buildFact(
                    methodId,
                    "http_mapping",
                    annotationExpr.toString(),
                    sourceFile,
                    annotationExpr,
                    "endpoint_extractor",
                    0.95D
            ));
        }
    }

    private void appendMethodCalls(
            String methodId,
            AstSourceFile sourceFile,
            MethodDeclaration methodDeclaration,
            AstExtractionResult extractionResult
    ) {
        for (MethodCallExpr methodCallExpr : methodDeclaration.findAll(MethodCallExpr.class)) {
            String targetId = methodCallExpr.getNameAsString();
            double confidence = 0.5D;
            try {
                targetId = methodCallExpr.resolve().getQualifiedSignature();
                confidence = 0.8D;
            }
            catch (UnsolvedSymbolException | UnsupportedOperationException exception) {
                extractionResult.addWarning(sourceFile.getRelativePath() + ":unresolved_call:" + methodCallExpr.getNameAsString());
            }
            catch (RuntimeException exception) {
                extractionResult.addWarning(sourceFile.getRelativePath() + ":call_resolve_failed:" + methodCallExpr.getNameAsString());
            }
            extractionResult.addRelation(buildRelation(
                    methodId,
                    "calls",
                    targetId,
                    sourceFile,
                    methodCallExpr,
                    confidence,
                    "call_extractor"
            ));
        }
    }

    private AstEntityType resolveEntityType(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof EnumDeclaration) {
            return AstEntityType.ENUM;
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classDeclaration && classDeclaration.isInterface()) {
            return AstEntityType.INTERFACE;
        }
        return AstEntityType.CLASS;
    }

    private AstFact buildFact(
            String entityId,
            String predicate,
            String value,
            AstSourceFile sourceFile,
            com.github.javaparser.ast.Node node,
            String extractor,
            double confidence
    ) {
        AstFact fact = new AstFact();
        fact.setEntityId(entityId);
        fact.setPredicate(predicate);
        fact.setValue(value);
        fact.setSourceRef(sourceFile.getRelativePath());
        fact.setSourceStartLine(beginLine(node));
        fact.setSourceEndLine(endLine(node));
        fact.setEvidenceExcerpt(extractExcerpt(node));
        fact.setConfidence(confidence);
        fact.setExtractor(extractor);
        return fact;
    }

    private AstRelation buildRelation(
            String srcId,
            String edgeType,
            String dstId,
            AstSourceFile sourceFile,
            com.github.javaparser.ast.Node node,
            double confidence,
            String extractor
    ) {
        AstRelation relation = new AstRelation();
        relation.setSrcId(srcId);
        relation.setEdgeType(edgeType);
        relation.setDstId(dstId);
        relation.setSourceRef(sourceFile.getRelativePath());
        relation.setSourceStartLine(beginLine(node));
        relation.setSourceEndLine(endLine(node));
        relation.setConfidence(confidence);
        relation.setExtractor(extractor);
        return relation;
    }

    private void persistSingleFile(
            AstSourceFile sourceFile,
            AstExtractionResult extractionResult,
            AstGraphExtractReport report
    ) {
        graphFactJdbcRepository.deleteBySourceRef(sourceFile.getRelativePath());
        graphRelationJdbcRepository.deleteBySourceRef(sourceFile.getRelativePath());
        graphEntityJdbcRepository.deleteBySourceFileId(sourceFile.getSourceFileId());
        for (AstEntity entity : extractionResult.getEntities()) {
            graphEntityJdbcRepository.upsert(entity);
            report.setEntityUpsertCount(report.getEntityUpsertCount() + 1);
        }
        for (AstFact fact : extractionResult.getFacts()) {
            graphFactJdbcRepository.insert(fact);
            report.setFactUpsertCount(report.getFactUpsertCount() + 1);
        }
        for (AstRelation relation : extractionResult.getRelations()) {
            graphRelationJdbcRepository.insert(relation);
            report.setRelationUpsertCount(report.getRelationUpsertCount() + 1);
        }
    }

    private int beginLine(com.github.javaparser.ast.Node node) {
        if (node == null || node.getRange().isEmpty()) {
            return 0;
        }
        return node.getRange().orElseThrow().begin.line;
    }

    private int endLine(com.github.javaparser.ast.Node node) {
        if (node == null || node.getRange().isEmpty()) {
            return 0;
        }
        return node.getRange().orElseThrow().end.line;
    }

    private String extractExcerpt(com.github.javaparser.ast.Node node) {
        if (node == null) {
            return "";
        }
        String excerpt = node.toString().replaceAll("\\s+", " ").trim();
        if (excerpt.length() <= 500) {
            return excerpt;
        }
        return excerpt.substring(0, 500);
    }
}
