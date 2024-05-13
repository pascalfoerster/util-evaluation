package de.featjar.evaluation;

import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.base.data.Pair;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.process.Result;
import de.featjar.evaluation.util.ModelReader;
import de.featjar.formula.analysis.VariableMap;
import de.featjar.formula.analysis.bool.*;
import de.featjar.formula.analysis.mig.solver.MIGBuilder;
import de.featjar.formula.analysis.mig.solver.ModalImplicationGraph;
import de.featjar.formula.analysis.sat4j.ASAT4JAnalysis;
import de.featjar.formula.analysis.sat4j.ComputeCoreSAT4J;
import de.featjar.formula.analysis.sat4j.indeterminate.*;
import de.featjar.formula.io.HiddenFormulaFormats;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.And;
import de.featjar.formula.structure.formula.connective.BiImplies;
import de.featjar.formula.structure.formula.connective.Or;
import de.featjar.formula.structure.formula.predicate.Literal;
import de.featjar.formula.transformer.ComputeCNFFormula;
import de.featjar.formula.transformer.ComputeNNFFormula;

import java.util.ArrayList;
import java.util.Arrays;
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
        CSVFile csvFile = this.addCSVWriter("data", "id", "count","imp-IA","update-pre1-IA");

        for (String modelName : models.keySet()) {
            FeatJAR.log().info("Running preprocess evaluation for " + modelName);
            Pair<IFormula, Pair<List<String>, List<BiImplies>>> model = models.get(modelName);
            IComputation<IFormula> formula = Computations.of(model.getKey());
            ComputeBooleanRepresentation<IFormula, IBooleanRepresentation> cnf =
                    formula.map(ComputeNNFFormula::new)
                            .map(ComputeCNFFormula::new)
                            .map(ComputeBooleanRepresentation::new);
            IComputation<BooleanClauseList> clauses = cnf.map(Computations::getKey).cast(BooleanClauseList.class);

            List<BooleanClause> temp = clauses.compute().stream().collect(Collectors.toSet()).stream().map(BooleanClause::new).collect(Collectors.toList());
            IComputation<BooleanClauseList> cnfS = Computations.of(new BooleanClauseList(temp,clauses.compute().getVariableCount()));
            VariableMap variableMap = cnf.map(Computations::getValue).compute();
            BooleanAssignment hiddenVariables = new BooleanAssignment(model.getValue().getKey().stream().mapToInt(x -> variableMap.get(x).get()).toArray());
            ModalImplicationGraph modalImplicationGraph = cnfS
                    .map(MIGBuilder::new).compute();
            int formulaSize = formula.getChildrenCount();
            int hiddenVariablesSize = hiddenVariables.size();
            // initialize data
            assert formulaSize == formula.getChildrenCount();
            assert hiddenVariablesSize == hiddenVariables.size();
            IFormula formula_simple = new And( cnfS.compute().stream().map(x -> new Or(x.stream().mapToObj(y -> new Literal(y>0,variableMap.get(Math.abs(y)).get())).collect(Collectors.toList()))).collect(Collectors.toList()));

            // initialise complete indeterminate analysis
            ComputeIndeterminate normalIndeterminate = new ComputeIndeterminate(cnfS);
            List<IFormula> andChilds = (List<IFormula>) formula.compute().getChildren();
            PreprocessIffSort preprocessIffSort = new PreprocessIffSort(Computations.of(new And(andChilds)));
            PreprocessImGraph preprocessImGraph = new PreprocessImGraph(Computations.of(modalImplicationGraph));
            ComputeBiImplicationFormula biImplicationFormula = new ComputeBiImplicationFormula(formula_simple, variableMap);
            List<Result<BooleanAssignment>> result = new ArrayList<>();
            result.add(compute(new Analysis(preprocessImGraph, variableMap, normalIndeterminate, hiddenVariables)));
          //  result.add(compute(new Analysis(biImplicationFormula, variableMap, preprocessIffSort, hiddenVariables)));
            List<String> outputLine = result.stream().map(e -> {
                if (e.getTime() == Long.MAX_VALUE) return "Timeout";
                return e.getResult().size() + "";
            }).collect(Collectors.toList());
            outputLine.add(0, modelName);
            outputLine.add(1, hiddenVariablesSize + "");
            outputLine.add(2, "");
            csvFile.addLine(outputLine);
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
            BooleanAssignment assignment;
            if(timeoutV > 0 ) {
                assignment = res.get(timeoutV, TimeUnit.MINUTES);
            }else {
                assignment = res.get();
            }
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
        public Analysis(ComputeBiImplicationFormula formula, VariableMap variableMap,  IndeterminatePreprocessFormula preprocess, BooleanAssignment hiddenVariables){
            this.hiddenVariables = hiddenVariables;
            this.preprocessF = preprocess;
            map = variableMap;
            this.formula = formula;
            type = 2;
        }

        @Override
        public BooleanAssignment call() throws Exception {
            if (type == 0) {
                return indeterminate
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST, hiddenVariables)
                        .compute();
            } else if(type == 1) {
                return preprocess.set(IndeterminatePreprocess.VARIABLE_MAP, map)
                        .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST, hiddenVariables)
                        .compute();
            }else {
                return  preprocessF.
                                addBiImplies(formula
                                    .set(ComputeBiImplicationFormula.MAXIMUM_CLAUSE_SIZE,30).compute())
                                .set(IndeterminatePreprocess.VARIABLE_MAP, map)
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST, hiddenVariables)
                                .compute();
            }
        }

        private String info() {
            switch (type) {
                case 0:
                    return " " + indeterminate;
                case 1:
                    return " " + preprocess + " => " + indeterminateAnalyse;
                default:
                    return " "+ formula+" => "+ preprocessF +" =>"+indeterminateAnalyse;
            }
        }
    }

}
