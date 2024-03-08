package de.featjar.evaluation;

import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.base.data.Pair;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.process.Result;
import de.featjar.evaluation.util.ModelReader;
import de.featjar.formula.analysis.VariableMap;
import de.featjar.formula.analysis.bool.BooleanAssignment;
import de.featjar.formula.analysis.bool.BooleanClauseList;
import de.featjar.formula.analysis.bool.ComputeBooleanRepresentation;
import de.featjar.formula.analysis.bool.IBooleanRepresentation;
import de.featjar.formula.analysis.mig.solver.MIGBuilder;
import de.featjar.formula.analysis.mig.solver.ModalImplicationGraph;
import de.featjar.formula.analysis.sat4j.ASAT4JAnalysis;
import de.featjar.formula.analysis.sat4j.ComputeCoreSAT4J;
import de.featjar.formula.analysis.sat4j.indeterminate.*;
import de.featjar.formula.io.HiddenFormulaFormats;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.BiImplies;
import de.featjar.formula.transformer.ComputeCNFFormula;
import de.featjar.formula.transformer.ComputeNNFFormula;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class IndeterminatePreprocessEvaluation extends Evaluator {
    private long timeoutV;
    private HashMap<String, Pair<IFormula, Pair<List<String>, List<BiImplies>>>> models = new HashMap<>();

    @Override
    public void init() throws Exception {
        super.init();
        timeoutV = optionParser.getResult(timeout).get();
        for (String modelName : systemNames) {
            ModelReader<Pair<IFormula, Pair<List<String>, List<BiImplies>>>> modelReader = new ModelReader<>(modelPath.resolve(modelName), HiddenFormulaFormats.getInstance());
            models.put(modelName, modelReader.loadFile(modelReader.getPathToFiles()).get());
        }
    }


    @Override
    protected void runEvaluation() throws Exception {
        CSVFile csvFile = this.addCSVWriter("data", "id", "count", "IA", "pre1-IA", "pre1DC-IA", "pre2-IA", "pre3-IA", "pre4-IA", "pre5-IA", "imp-IA");

        for (String modelName : models.keySet()) {
            FeatJAR.log().info("Running preprocess evaluation for " + modelName);
            Pair<IFormula, Pair<List<String>, List<BiImplies>>> model = models.get(modelName);
            IComputation<IFormula> formula = Computations.of(model.getKey());
            ComputeBooleanRepresentation<IFormula, IBooleanRepresentation> cnf =
                    formula.map(ComputeNNFFormula::new)
                            .map(ComputeCNFFormula::new)
                            .map(ComputeBooleanRepresentation::new);
            IComputation<BooleanClauseList> clauses = cnf.map(Computations::getKey).cast(BooleanClauseList.class);
            VariableMap variableMap = cnf.map(Computations::getValue).compute();
            BooleanAssignment hiddenVariables = new BooleanAssignment(model.getValue().getKey().stream().mapToInt(x -> variableMap.get(x).get()).toArray());
            ModalImplicationGraph modalImplicationGraph = clauses
                    .map(MIGBuilder::new).compute();
            List<Integer> indexes = optionParser.getResult(algorithmIterationsOption).get();
            BooleanAssignment deadCore = new ComputeCoreSAT4J(clauses).compute();
            int formulaSize = formula.getChildrenCount();
            int hiddenVariablesSize = hiddenVariables.size();
            // initialize data
            assert formulaSize == formula.getChildrenCount();
            assert hiddenVariablesSize == hiddenVariables.size();
            // initialise complete indeterminate analysis
            ComputeIndeterminate normalIndeterminate = new ComputeIndeterminate(clauses);
            //initalize core Dead  Analyse
            ComputeCoreSAT4J computeCoreSAT4J = new ComputeCoreSAT4J(clauses);
            // initialise pre process
            PreprocessIff preprocessIff = new PreprocessIff(formula);
            PreprocessIff preprocessIff1 = new PreprocessIff(formula);
            preprocessIff1.set(PreprocessIff.CORE_DEAD_FEATURE, deadCore);
            PreprocessIffV2 preprocessIffV2 = new PreprocessIffV2(formula);
            PreprocessIffSort preprocessIffSort = new PreprocessIffSort(formula);
            PreprocessIffComp preprocessIffComp = new PreprocessIffComp(formula);
            PreprocessIffCompSort preprocessIffCompSort = new PreprocessIffCompSort(formula);
            PreprocessImGraph preprocessImGraph = new PreprocessImGraph(Computations.of(modalImplicationGraph));
            List<Result<BooleanAssignment>> result = new ArrayList<>();
            result.add(compute(new Analysis(normalIndeterminate, hiddenVariables)));
            result.add(compute(new Analysis(preprocessIff, variableMap, normalIndeterminate, hiddenVariables)));
            result.add(compute(new Analysis(preprocessIff1, variableMap, normalIndeterminate, hiddenVariables)));
            result.add(compute(new Analysis(preprocessIffV2, variableMap, normalIndeterminate, hiddenVariables)));
            result.add(compute(new Analysis(preprocessIffSort, variableMap, normalIndeterminate, hiddenVariables)));
            result.add(compute(new Analysis(preprocessIffComp, variableMap, normalIndeterminate, hiddenVariables)));
            result.add(compute(new Analysis(preprocessIffCompSort, variableMap, normalIndeterminate, hiddenVariables)));
            result.add(compute(new Analysis(preprocessImGraph, variableMap, normalIndeterminate, hiddenVariables)));
            List<String> outputLine = result.stream().map(e -> {
                if (e.getTime() == Long.MAX_VALUE) return "Timeout";
                return e.getResult().size() + "";
            }).collect(Collectors.toList());
            outputLine.add(0, modelName);
            outputLine.add(1, hiddenVariablesSize + "");
            csvFile.addLine(outputLine);
            FeatJAR.log().info("");

            csvFile.flush();
            FeatJAR.log().info("Finished evaluation for " + modelName);
        }

    }


    private Result<BooleanAssignment> compute(Analysis analysis) {
        Result<BooleanAssignment> result = new Result<>();
        long start, end = 0;
        start = System.nanoTime();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<BooleanAssignment> res = executorService.submit(analysis);
        try {
            BooleanAssignment assignment = res.get(timeoutV, TimeUnit.MINUTES);
            end = System.nanoTime();
            result.setResult(assignment);
            result.setTime(end - start);
            //    FeatJAR.log().info("Finished:"+analysis.info());
        } catch (TimeoutException e) {
            res.cancel(true);
            result.setTime(Long.MAX_VALUE);
            result.setResult(null);
            //   FeatJAR.log().info("Timeout: "+analysis.info());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdownNow();
        }

        return result;
    }

    private class Analysis implements Callable<BooleanAssignment> {
        ASAT4JAnalysis.Solution<BooleanAssignment> indeterminate;
        ComputeIndeterminate indeterminateAnalyse;
        IndeterminatePreprocessFormula preprocessF;
        IndeterminatePreprocess preprocess;
        ComputeBiImplicationFormula formula;
        int type = 0;
        VariableMap map;
        BooleanAssignment hiddenVariables;

        ComputeCoreSAT4J coreDeadAnalysis;

        public Analysis(ASAT4JAnalysis.Solution<BooleanAssignment> indeterminate, BooleanAssignment hiddenVariables) {
            this.indeterminate = indeterminate;
            this.hiddenVariables = hiddenVariables;
            type = 0;
        }

        public Analysis(IndeterminatePreprocess preprocess, VariableMap variableMap, ComputeIndeterminate indeterminate, BooleanAssignment hiddenVariables) {
            this.indeterminateAnalyse = indeterminate;
            this.hiddenVariables = hiddenVariables;
            this.preprocess = preprocess;
            map = variableMap;
            type = 1;
        }


        @Override
        public BooleanAssignment call() throws Exception {
            if (type == 0) {
                return indeterminate
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST, hiddenVariables)
                        .compute();
            } else {
                return preprocess.set(IndeterminatePreprocess.VARIABLE_MAP, map)
                        .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST, hiddenVariables)
                        .compute();
            }
        }

        private String info() {
            switch (type) {
                case 0:
                    return " " + indeterminate;
                default:
                    return " " + preprocess + " => " + indeterminateAnalyse;

            }
        }
    }

}
