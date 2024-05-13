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
import de.featjar.formula.io.FormulaFormats;
import de.featjar.formula.io.HiddenFormulaFormats;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.BiImplies;
import de.featjar.formula.transformer.ComputeCNFFormula;
import de.featjar.formula.transformer.ComputeNNFFormula;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class IndeterminateEvaluation extends Evaluator {
    private final HashMap< String, Pair< IFormula, Pair<List<String>,List< BiImplies >>>> models = new HashMap<>();


    @Override
    public void init() throws Exception {
        super.init();
        for(String modelName : systemNames) {
            ModelReader<Pair<IFormula, Pair<List<String>, List<BiImplies>>>> modelReader = new ModelReader<>(modelPath.resolve(modelName), HiddenFormulaFormats.getInstance());
            models.put(modelName, modelReader.loadFile(modelReader.getPathToFiles()).get());
        }
    }



    @Override
    protected void runEvaluation() throws Exception {
        for(String modelName: models.keySet() ) {
            FeatJAR.log().info("Running evaluation for "+modelName);
            Pair< IFormula, Pair<List<String>,List< BiImplies >>> model = models.get(modelName);
            CSVFile csvFile = this.addCSVWriter(modelName, "id","IA", "pre1-IA","pre1DCBe-IA","pre1DCDu-IA", "pre2-IA", "pre3-IA", "pre4-IA", "pre5-IA", "impB-IA", "impA-IA","CorrectRes");
            IComputation<IFormula> formula = Computations.of(model.getKey());
            ComputeBooleanRepresentation<IFormula, IBooleanRepresentation> cnf =
                    formula.map(ComputeNNFFormula::new)
                            .map(ComputeCNFFormula::new)
                            .map(ComputeBooleanRepresentation::new);
            IComputation<BooleanClauseList> clauses = cnf.map(Computations::getKey).cast(BooleanClauseList.class);
            BooleanClauseList clauses1 = clauses.compute();
            List<BooleanClause> temp = clauses1.stream().collect(Collectors.toSet()).stream().map(BooleanClause::new).collect(Collectors.toList());
            IComputation<BooleanClauseList> cnfS = Computations.of(new BooleanClauseList(temp,clauses1.getVariableCount()));
            VariableMap variableMap = cnf.map(Computations::getValue).compute();
            BooleanAssignment hiddenVariables = new BooleanAssignment(model.getValue().getKey().stream().mapToInt(x -> variableMap.get(x).get()).toArray());
            MIGBuilder migBuilder=  cnfS
                    .map(MIGBuilder::new);
            ModalImplicationGraph modalImplicationGraph = migBuilder.compute();
            List<Integer> indexes = optionParser.getResult(algorithmIterationsOption).get();
            BooleanAssignment deadCore = new ComputeCoreSAT4J(clauses).compute();
            int formulaSize = formula.getChildrenCount();
            int hiddenVariablesSize  = hiddenVariables.size();

            for (int i : indexes) {
                // initialize data
                assert formulaSize == formula.getChildrenCount();
                assert hiddenVariablesSize == hiddenVariables.size();
                // initialise complete indeterminate analysis
                ComputeIndeterminate normalIndeterminate = new ComputeIndeterminate(cnfS);
                // initialise pre process
                PreprocessIff preprocessIff = new PreprocessIff(formula);
                PreprocessIff preprocessIff1 = new PreprocessIff(formula);
                preprocessIff1.set(PreprocessIff.CORE_DEAD_FEATURE, deadCore);
                PreprocessIff preprocessIff2 = new PreprocessIff(formula);
                PreprocessIffV2 preprocessIffV2 = new PreprocessIffV2(formula);
                PreprocessIffSort preprocessIffSort = new PreprocessIffSort(formula);
                PreprocessIffComp preprocessIffComp = new PreprocessIffComp(formula);
                PreprocessIffCompSort preprocessIffCompSort = new PreprocessIffCompSort(formula);
                PreprocessImGraph preprocessImGraph = new PreprocessImGraph(Computations.of(modalImplicationGraph));
                List<Result<BooleanAssignment>> result = new ArrayList<>();
                result.add(compute(new Analysis(normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIff, variableMap, clauses1, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIff1, variableMap, clauses1, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIff2, variableMap, clauses1, hiddenVariables,true)));
                result.add(compute(new Analysis(preprocessIffV2, variableMap, clauses1, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIffSort, variableMap, clauses1, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIffComp, variableMap, clauses1, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIffCompSort, variableMap, clauses1, hiddenVariables)));
                result.add(compute(new Analysis(preprocessImGraph, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(migBuilder, normalIndeterminate, hiddenVariables)));

                int correct = result.size();
                List<Integer> res = null;
                for(int j = 0; j < result.size();j++ ) {

                    if(result.get(j).getResult() != null) {
                        if( res == null) {
                            res = result.get(j).getResult().streamValues().map(Pair::getKey).sorted().collect(Collectors.toList());
                        }else {
                            List<Integer> otherRes = result.get(j).getResult().streamValues().map(Pair::getKey).sorted().collect(Collectors.toList());
                            if (!otherRes.equals(res)) {
                                List<Integer> finalRes = res;
                                List<Integer> wrong = otherRes.stream().filter(x -> !finalRes.contains(x)).collect(Collectors.toList());
                                FeatJAR.log().info(j + ": " + otherRes.size() + " " + res.size());
                                wrong.addAll(res.stream().filter(x -> !otherRes.contains(x)).collect(Collectors.toList()));
                                FeatJAR.log().info(j + ": " + wrong);
                                List<String> wrongName = wrong.stream().map(x -> variableMap.get(x).get()).collect(Collectors.toList());
                                FeatJAR.log().info(j + ": " + wrongName);
                                correct--;
                            }
                        }
                    }
                }
                List<String> outputLine = result.stream().map(e -> {
                            return TimeUnit.MILLISECONDS.convert(e.getTime(), TimeUnit.NANOSECONDS) + "";
                        }
                ).collect(Collectors.toList());
                outputLine.add(0,i+"");
                outputLine.add(correct+"");

                csvFile.addLine(outputLine);
            }
            csvFile.flush();
            FeatJAR.log().info("Finished evaluation for "+modelName);
        }

    }



    private Result<BooleanAssignment> compute(Analysis analysis){
        Result<BooleanAssignment> result = new Result<>();
        long start, end;
        start = System.nanoTime();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<BooleanAssignment> res =  executorService.submit(analysis);
        try {
            BooleanAssignment assignment;

            assignment = res.get();
            end = System.nanoTime();
            result.setResult(assignment);
            result.setTime(end-start);
        }catch (InterruptedException| ExecutionException e ){
            e.printStackTrace();
        } finally{
            executorService.shutdownNow();
        }

        return result;
    }

    private static class Analysis implements Callable<BooleanAssignment>{
        ASAT4JAnalysis.Solution<BooleanAssignment> indeterminate;
        ComputeIndeterminate indeterminateAnalyse;
        IndeterminatePreprocessFormula preprocessF;
        IndeterminatePreprocess preprocess;
        PreprocessImGraph preprocessImGraph;
        ComputeBiImplicationFormula formula;
        int type;
        VariableMap map;
        BooleanAssignment hiddenVariables;

        BooleanClauseList clauses;

        MIGBuilder migBuilder;

        public Analysis(ASAT4JAnalysis.Solution<BooleanAssignment> indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminate = indeterminate;
            this.hiddenVariables = hiddenVariables;
            type = 0;
        }
        public Analysis(IndeterminatePreprocess preprocess, VariableMap variableMap, BooleanClauseList clauses, BooleanAssignment hiddenVariables){
            this.clauses = clauses;
            this.hiddenVariables = hiddenVariables;
            this.preprocess = preprocess;
            map = variableMap;
            type = 1;
        }
        public Analysis( IndeterminatePreprocess preprocess, VariableMap variableMap, BooleanClauseList clauses, BooleanAssignment hiddenVariables,boolean coreDead){
            this.clauses = clauses;
            this.hiddenVariables = hiddenVariables;
            this.preprocess = preprocess;
            map = variableMap;
            type = 2;
        }
        public Analysis(MIGBuilder migBuilder, ComputeIndeterminate indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminateAnalyse = indeterminate;
            this.hiddenVariables = hiddenVariables;
            this.migBuilder = migBuilder;
            type = 3;
        }
        public Analysis(IndeterminatePreprocess preprocess, VariableMap variableMap, ComputeIndeterminate indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminateAnalyse = indeterminate;
            this.hiddenVariables = hiddenVariables;
            this.preprocess = preprocess;
            map = variableMap;
            type = 4;
        }

        @Override
        public BooleanAssignment call() {
            if(type == 0){
                return indeterminate
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST, hiddenVariables)
                        .compute();
            }else if(type == 1){
                List<BooleanClause> temp = clauses.stream().collect(Collectors.toSet()).stream().map(BooleanClause::new).collect(Collectors.toList());
                BooleanClauseList cnfS = new BooleanClauseList(temp,clauses.getVariableCount());
                return new ComputeIndeterminate(Computations.of(cnfS))
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST,preprocess
                                .set(IndeterminatePreprocess.VARIABLE_MAP,map)
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST,hiddenVariables)
                                .compute())
                        .compute();
            }else if(type == 2){
                List<BooleanClause> temp = clauses.stream().collect(Collectors.toSet()).stream().map(BooleanClause::new).collect(Collectors.toList());
                BooleanClauseList cnfS = new BooleanClauseList(temp,clauses.getVariableCount());
                return new ComputeIndeterminate(Computations.of(cnfS))
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST,preprocess
                                .set(IndeterminatePreprocess.VARIABLE_MAP,map)
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST,hiddenVariables)
                                .set(IndeterminatePreprocess.CORE_DEAD_FEATURE,new ComputeCoreSAT4J(Computations.of(cnfS)).compute())
                                .compute())
                        .compute();
            }else if (type == 3) {
                preprocessImGraph = new PreprocessImGraph(Computations.of(migBuilder.compute()));
                return indeterminateAnalyse
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST,preprocessImGraph
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST,hiddenVariables)
                                .compute())
                        .compute();
            }else{
                return indeterminateAnalyse
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST,preprocess
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST,hiddenVariables)
                                .compute())
                        .compute();
            }

        }
    }

}

