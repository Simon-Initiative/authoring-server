/* 
 * @(#)activity.js.java $Date: 2015/00/18 
 * 
 * Copyright (c) 2015 Carnegie Mellon University.
 */

define(function () {
    function ActivityEmbed() {
        var title = null, assets = {}, questionList = {}, answerList = {}, feedbackList = {}, hintsList = {}, questionsSaveData = new SaveData();
        var currentQuestion = null;
        var currentPart = null;
        var superClient = null;
        var initEditorText = null;

        this.init = function (sSuperClient, activityData) {
            superClient = sSuperClient;
            title = $(activityData).find("title").text();
            $(activityData).find("assets").children("asset").each(function (index) {
                console.log("asset name " + $(this).attr("name") + " value " + $(this).text());
                assets[$(this).attr("name")] = $(this).text();
            });
            this.render();
        };
        this.render = function () {
            $("<link/>", {
                rel: "stylesheet",
                type: "text/css",
                href: 'https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css'
            }).appendTo("head");
            $("<link/>", {
                rel: "stylesheet",
                type: "text/css",
                href: superClient.webContentFolder + assets.emstyles
            }).appendTo("head");
            $("<script/>", {
                type: "text/javascript",
                src: 'https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js'
            }).appendTo("head");
            $.get(superClient.webContentFolder + assets.layout, function (layout) {
                $('#oli-embed').append(layout);

            });

            $.get(superClient.webContentFolder + assets.questions, function (data) {
                $(data).find("question").each(function (q) {
                    var q1 = new Question($(this).attr("id"), 'none');
                    console.log("question id" + q1.getId());
                    questionList[q1.getId()] = q1;
                    $(this).find("part").each(function (e) {
                        var p1 = new Part($(this).attr("id"), 'console', $(this));
                        q1.addInput(p1);
                        var answers = {};
                        $(this).find("answer").each(function (e) {
                            answers[$(this).attr("id")] = {id: $(this).attr("id"), score: $(this).attr('score'), answer: $(this).text()};
                        });
                        answerList[$(this).attr("id")] = answers;
                        var hints = {};
                        $(this).find("hint").each(function (e) {
                            var hint = {id: $(this).attr("id"), content: $(this).html()};
                            hints[$(this).attr("id")] = hint;
                            p1.addHint(hint);
                        });
                        hintsList[$(this).attr("id")] = hints;
                        var feedbacks = {};
                        $(this).find("feedback").each(function (e) {
                            var feedback = {id: $(this).attr("id"), content: $(this).html(), pattern: $(this).attr("match")};
                            feedbacks[$(this).attr("id")] = feedback;
                            p1.addFeedback(feedback);
                        });
                        feedbackList[$(this).attr("id")] = feedbacks;
                        if ($(this).find("feedbackengine").length) {
                            p1.feedbackEngines = {};
                            var cloudCoderData = $(this).find("cloudcoder");
                            if (typeof (cloudCoderData) !== "undefined" && cloudCoderData !== null) {
                                p1.feedbackEngines['cloudcoder'] = cloudCoderData;
                            }
                        }
                        if ($(this).find("initeditortext").length) {
                            initEditorText = $(this).find("initeditortext").text().trim();
                        }
                    });
                });
                activityEmbed.loadControls();
            });

            require.config({
                paths: {ace: ['/superactivity/ace_editor/ace/'],
                    highlight: ['/syntaxhighlighter/']},
            });
            require(['ace/ace'], function (ace) {
                ActivityEmbed.editor = ace.edit("editor");
                ActivityEmbed.editor.setTheme("ace/theme/chrome");
                ActivityEmbed.editor.getSession().setMode("ace/mode/python");
                ActivityEmbed.editor.setAutoScrollEditorIntoView(false);
                ActivityEmbed.editor.renderer.setScrollMargin(10, 10, 10, 10);
                if (initEditorText !== null) {
                    ActivityEmbed.editor.setValue(initEditorText);
                }
            });

        };
        this.loadControls = function () {
            $.get(superClient.webContentFolder + assets.controls, function (controls) {
                $('#oli-embed').append(controls);
                $("#save_btn").click(function () {
                    if (!superClient.isCurrentAttemptCompleted() && questionsSaveData.numberOfQuestionsAnswered() < 1) {
                        activityEmbed.save();
                    }
                });
                $("#submit_btn").click(function () {
                    if (!superClient.isCurrentAttemptCompleted() && questionsSaveData.numberOfQuestionsAnswered() < 1) {
                        activityEmbed.submit();
                    }
                });
                $("#hint_btn").click(function () {
                    if (!superClient.isCurrentAttemptCompleted()
                        && typeof (currentPart) === "undefined" || currentPart === null || currentPart.getHints().length > 0) {
                        activityEmbed.hint();
                    }
                });
                $("#next_btn").click(function () {
                    if (superClient.isCurrentAttemptCompleted()) {
                        activityEmbed.nextAttempt();
                    }
                });
                $("#solution_btn").click(function () {
                    if (superClient.isCurrentAttemptCompleted() || activityEmbed.isUngradedActivity()) {
                        $.get(superClient.webContentFolder + assets.solutions, function (data) {
                            var solutionText = null;
                            $(data).find("solution").each(function (q) {
                                solutionText = $(this).text().trim();
                                solutionText = solutionText + "\n";
                            });
                            if (typeof (ActivityEmbed.solution) !== "undefined" && ActivityEmbed.solution !== null) {
                                if (solutionText !== null) {
                                    ActivityEmbed.solution.setValue(solutionText);
                                    $('#' + currentQuestion.getId() + '_solutions').show();
                                }
                            } else {
                                require(['ace/ace'], function (ace) {
                                    ActivityEmbed.solution = ace.edit(currentQuestion.getId() + '_solutions');
                                    ActivityEmbed.solution.setTheme("ace/theme/chrome");
                                    ActivityEmbed.solution.getSession().setMode("ace/mode/python");
                                    ActivityEmbed.solution.setAutoScrollEditorIntoView(false);
                                    ActivityEmbed.solution.renderer.setScrollMargin(10, 10, 10, 10);
                                    ActivityEmbed.solution.setReadOnly(true);
                                    ActivityEmbed.solution.renderer.setShowGutter(false);
                                    if (solutionText !== null) {
                                        ActivityEmbed.solution.setValue(solutionText);
                                        $('#' + currentQuestion.getId() + '_solutions').show();
                                    }
                                });
                            }
                        });
                    }
                });

                $("#run").click(function () {
                    if (!superClient.isCurrentAttemptCompleted()) {
                        var edit_text = ActivityEmbed.editor.getValue();
                        activityEmbed.pushInput(edit_text);

                        // clear any exisitng content that might be in the buffer
                        ActivityEmbed.repl.clearScreen();
                        ActivityEmbed.repl.writeln(edit_text);

                        activityEmbed.submit();
                    }

                });

                $("#clear").click(function () {
                    if (!superClient.isCurrentAttemptCompleted()) {
                        ActivityEmbed.repl.clearScreen();
                        ActivityEmbed.repl.writeStdin('\n');
                        ActivityEmbed.repl.disableStdin(false);
                    }
                });

                activityEmbed.processLayout();

            });
        };
        this.processLayout = function () {
            require(['//repl.oli.cmu.edu/repl/api/v2/libs/repl-client.js'], function (js) {
                var q1, p1, x = 0;
                for (var k in questionList) {
                    if (questionList.hasOwnProperty(k)) {

                        q1 = questionList[k];
                        console.log("value " + q1);

                        var questionData = new QuestionData(q1.getId());
                        questionsSaveData.addQuestionData(questionData);
                        var questionParts = q1.getParts();
                        for (var j in questionParts) {
                            if (questionParts.hasOwnProperty(j)) {
                                p1 = questionParts[j];
                                currentPart = p1;
                                break;
                            }
                        }

                        currentQuestion = q1;
                        var input = {id: '_blank', value: '_blank'};
                        var partData = new PartData(p1.getId(), input);
                        var bAnswer = activityEmbed.findAnswerForInput(p1.getId(), input.id.toLowerCase());
                        if (bAnswer !== null) {
                            partData.setCorrect(true);
                            partData.setScore(Number(bAnswer.score));
                        } else {
                            partData.setCorrect(false);
                            partData.setScore(0);
                        }
                        
                        questionData.addPartData(partData);
                        partData.setFeedback(p1.getFeedbackForAnswerId(input.id));
                        $('#oli-embed').append('<div id="' + questionData.getId() + '_solutions" style="margin-top:5px;width: 645px;height: 170px;border: 1px solid #ece60d;"><!--<div class="content"/>--></div>');
                        $('#' + questionData.getId() + '_solutions').hide();
                        $('#oli-embed').append('<div id="' + questionData.getId() + '_feedback" class="feedback"><p style="font-weight: bold;">' +
                                ' Feedback</p></div>');
                        $('#oli-embed').append('<div id="' + questionData.getId() + '_hints" class="hints"><p style="font-weight: bold;">' +
                                'Hint</p><div class="content"/><a class="next" href="javascript();">Next</a></div>');
                        $('#' + questionData.getId() + '_hints').find('.next').click(function (e) {
                            e.preventDefault();
                            activityEmbed.hint();
                        });
                        break;
                    }
                }

                if (q1 === null || p1 === null) {
                    throw "Question and Part related to this activity not found";
                }

                // create a new REPL activity client
                ActivityEmbed.repl = new ReplClient({
                    host: 'repl.oli.cmu.edu',
                    language: 'python3',
                    terminalOptions: {
                        convertEol: true,
                        cursorBlink: true,
                        fontSize: 12,
                    },
                });

                if (superClient.isCurrentAttemptCompleted()) {
                    ActivityEmbed.repl.disableStdin();
                    ActivityEmbed.repl.disableStdout();
                } 

                ActivityEmbed.repl.attach(document.getElementById('console'));

                $('pre').css('background-color', 'transparent');
                activityEmbed.restoreSavedFile();
            });
        };
        this.isUngradedActivity = function () {
            // check if there was a question and part to process. If not, then this is treated as
            // an ungraded activity
            if (typeof (currentQuestion) === "undefined" || currentQuestion === null
                    || typeof (currentPart) === "undefined" || currentPart === null) {
                console.log("currentQuestion or currentPart not set");
                return true;
            }
        };
        this.pushInput = function (input) {
            if (activityEmbed.isUngradedActivity()) {
                console.log("no question and part, activity will not be graded");
                return;
            }
            console.log("pushInput(): " + input);
            var questionData = questionsSaveData.getQuestionData(currentQuestion.getId());
            if (questionData === null) {
                questionData = new QuestionData(currentQuestion.getId());
                questionsSaveData.addQuestionData(questionData);
            }
            var partData = questionData.getPartData(currentPart.getId());
            console.log("PartData " + partData.getInput().id + " " + partData.getInput().value);
            if (partData === null || partData.getInput().id === "_blank") {
                var inputval = {id: 'text', value: input};
                partData = new PartData(currentPart.getId(), inputval);
                partData.setCorrect(false);
                partData.setScore(0);
                questionData.addPartData(partData);
                partData.setFeedback(currentPart.getFeedbackForAnswerId(inputval.id));
            } else {
                partData.setInput(partData.getInput().value + '\n' + input);
            }
            activityEmbed.controls();

            var action = new ActionLog(CommonLogActionNames.START_STEP, superClient.sessionId, superClient.resourceId, superClient.activityGuid, "REPL_ACTIVITY", superClient.timeZone);
            // Important: allows dashboard tracking
            var supplement = new SupplementLog();
            supplement.setAction(CommonLogActionNames.START_STEP_TRACK);
            supplement.setSource(currentQuestion.getId());
            supplement.setInfoType(currentPart.getId());
            supplement.setInfo("step started");
            action.addSupplement(supplement);
            superClient.logAction(action);
        };
        this.findAnswerForInput = function (partId, input) {
            var answers = answerList[partId];
            if (answers) {
                for (var k in answers) {
                    if (answers.hasOwnProperty(k)) {
                        var ans = answers[k];
                        console.log("Answer for part " + partId + " equals " + ans.answer.toLowerCase());
                        if (ans.answer.toLowerCase() === input) {
                            console.log("Answer for part " + partId + " found " + ans.answer.toLowerCase());
                            return ans;
                        }
                    }
                }
            }
            return null;
        };
        this.restoreSavedFile = function () {
            console.log("restoreData()");
            if (typeof (superClient.currentAttempt) !== "undefined" && superClient.currentAttempt !== null
                    && superClient.currentAttempt !== 'none') {
                if (superClient.fileRecordList.hasOwnProperty('student_save_file' + superClient.currentAttempt)) {
                    superClient.loadFileRecord('student_save_file', superClient.currentAttempt, function (response) {
                        console.log("student_save_file Data response: " + response);

                        $(response).find('save_data').find('question').each(function (i, val) {
                            // Only process one part for this
                            if (i > 0) {
                                return false;
                            }
                            console.log("restore id " + $(this).attr('id'));
                            var questionData = new QuestionData($(this).attr('id'));
                            if ($(this).attr('score')) {
                                console.log("restore score " + $(this).attr('score'));
                                questionData.setScore(Number($(this).attr('score')));
                            }
                            $(this).find('part').each(function (index, value) {
                                // Only process one part for this
                                if (index > 0) {
                                    return false;
                                }
                                var input = {id: $(this).find('input').attr('id'), value: $(this).find('input').text()};
                                var partId = $(this).attr('id');
                                var partData = new PartData(partId, input);
                                if ($(this).attr('correct') === 'true') {
                                    partData.setCorrect(true);
                                } else {
                                    partData.setCorrect(false);
                                }
                                partData.setScore(Number($(this).attr('score')));
                                if ($(this).find('feedback')) {
                                    var feedback = {id: $(this).find('feedback').attr("id"), content: $(this).find('feedback').text()};
                                    partData.setFeedback(feedback);
                                }
                                if ($(this).find('hint')) {
                                    var hint = {id: $(this).find('hint').attr("id"), content: $(this).find('hint').text()};
                                    partData.setHint(hint);
                                }
                                questionData.addPartData(partData);
                                if (superClient.isCurrentAttemptCompleted()) {
                                    if (partData.getFeedback() !== null) {
                                        $('#' + questionData.getId() + '_feedback').append('<div style="display: inline-block;">' + partData.getFeedback().content + '</div>');
                                        if (partData.getCorrect()) {
                                            var styles = {
                                                background: '#ddffdd',
                                                borderColor: '#33aa33',
                                                display: 'block'
                                            };
                                            $('#' + questionData.getId() + '_feedback').css(styles);
                                        } else {
                                            var styles = {
                                                background: '#f4cfc9',
                                                borderColor: '#e75d36',
                                                display: 'block'
                                            };
                                            $('#' + questionData.getId() + '_feedback').css(styles);
                                        }
                                    }
                                }
                                
                                // restore content to editor
                                initEditorText = input.value;

                                // write previous input/output content
                                ActivityEmbed.repl.on('afterconnect', () => {
                                    ActivityEmbed.repl.clearScreen();
                                    ActivityEmbed.repl.writeln(input.value)
                                    ActivityEmbed.repl.writeln($(this).find('output').text());
                                });

                            });
                            questionsSaveData.addQuestionData(questionData);

                        });
                        activityEmbed.controls();
                    });
                } else {
                    activityEmbed.controls();
                }
            }
        };
        this.controls = function () {
            console.log("controls()");
            if (superClient.isCurrentAttemptCompleted()) {
                $('#save_btn').addClass('disabled');
                $('#submit_btn').addClass('disabled');
                $('#run').addClass('disabled');
                $('#clear').addClass('disabled');
                $('#hint_btn').addClass('disabled');

                $('#next_btn').removeClass('disabled');
                $("#solution_btn").removeClass('disabled');
            } else {
                if (questionsSaveData.numberOfQuestionsAnswered() > 0) {
                    $('#save_btn').removeClass('disabled');
                    $('#submit_btn').removeClass('disabled');
                } else {
                    $('#save_btn').addClass('disabled');
                    $('#submit_btn').addClass('disabled');
                }
                if (typeof (currentPart) === "undefined" || currentPart === null || currentPart.getHints().length === 0) {
                    $('#hint_btn').addClass('disabled');
                } else {
                    $('#hint_btn').removeClass('disabled');
                }
                $('#next_btn').addClass('disabled');
                $('#run').removeClass('disabled');
                $('#clear').removeClass('disabled');
            }
            if (Number(superClient.currentAttempt) > 1 || activityEmbed.isUngradedActivity()) {
                $("#solution_btn").removeClass('disabled');
            } else {
                $("#solution_btn").addClass('disabled');
            }
            if (superClient.maxAttempts > 0 && superClient.maxAttempts <= Number(superClient.currentAttempt)) {
                $('#next_btn').addClass('disabled');
            }
        };
        this.save = function () {
            console.log("save()");
            if (superClient.isCurrentAttemptCompleted()) {
                return;
            }
            var xml = questionsSaveData.toXML();
            var saveData = (new XMLSerializer()).serializeToString(xml.context);
            console.log("save() " + saveData);
            superClient.writeFileRecord('student_save_file', 'text/xml', superClient.currentAttempt, saveData, function (response) {
                console.log("WriteFileRecord Data server response: " + response);
            });
            var action = new ActionLog(CommonLogActionNames.SAVE_ATTEMPT, superClient.sessionId, superClient.resourceId, superClient.activityGuid, "REPL_ACTIVITY", superClient.timeZone);
            var supplement = new SupplementLog();
            supplement.setAction(CommonLogActionNames.SAVE_ATTEMPT);
            supplement.setInfoType('attempt');
            supplement.setInfo(superClient.currentAttempt);
            action.addSupplement(supplement);
            superClient.logAction(action);

        };
        this.submit = function () {
            console.log("submit()");
            if (superClient.isCurrentAttemptCompleted()) {
                return;
            }

            ActivityEmbed.repl.disableStdin();
            return activityEmbed.process();
        };
        this.process = function () {
            var questionData;
            var partData;
            var code;
            if (activityEmbed.isUngradedActivity()) {
                code = ActivityEmbed.editor.getValue();
            } else {
                questionData = questionsSaveData.getQuestionData(currentQuestion.getId());
                partData = questionData.getPartData(currentPart.getId());
                code = partData.getInput().value
            }

            return ReplClient.exec(code, { host: 'repl.oli.cmu.edu', language: 'python3' })
                .then(function (result) {
                    console.log("Ouput from ReplClient.exec " + JSON.stringify(result));
                    ActivityEmbed.repl.writeln('---------------------------------------');
                    ActivityEmbed.repl.write(result.combined);

                    // check if there was a question and part to process. If not, then this is treated as
                    // an ungraded activity, so simply return
                    if (activityEmbed.isUngradedActivity()) {
                        console.log("currentQuestion or currentPart not set");
                        return;
                    }

                    // Only score attempt if at least 1 question has been answered
                    if (questionsSaveData.numberOfQuestionsAnswered() < 1) {
                        console.log("scoreAttempt() no questions answered ");
                        return;
                    }

                    if (!(result.error)) {
                        console.log("No error from ReplClient.exec");
                        var feedbackEngines = currentPart.feedbackEngines;
                        if (typeof (feedbackEngines) !== "undefined" && feedbackEngines !== null) {
                            for (var k in feedbackEngines) {
                                if (feedbackEngines.hasOwnProperty(k)) {
                                    if (k === "cloudcoder") {
                                        console.log("Found cloud coder data");
                                        var info = feedbackEngines[k];
                                        activityEmbed.cloudCoderProcess(info);
                                    }
                                }
                            }
                        } else {
                            var feedback = {id: "replt_eval", content: "Correct: code is well formated"};
                            partData.setFeedback(feedback);
                            partData.setScore(Number(10));
                            partData.setCorrect(true);
                            $('#' + currentQuestion.getId() + '_feedback').append('<div style="display: inline-block;">' + partData.getFeedback().content + '</div>');
                            var styles = {
                                background: '#ddffdd',
                                borderColor: '#33aa33',
                                display: 'block'
                            };
                            $('#' + currentQuestion.getId() + '_feedback').css(styles);
                            activityEmbed.saveDataAndScore();
                        }
                    } else {
                        partData.setOutput(result.combined);
                        partData.setCorrect(false);
                        partData.setScore(0);
                        var feedback = {id: "error_feedback", content: "Execution failed: " + result.stderr};
                        partData.setFeedback(feedback);
                        $('#' + currentQuestion.getId() + '_feedback').append('<div style="display: inline-block;">' + partData.getFeedback().content + '</div>');
                        var styles = {
                            background: '#f4cfc9',
                            borderColor: '#e75d36',
                            display: 'block'
                        };
                        $('#' + currentQuestion.getId() + '_feedback').css(styles);

                        activityEmbed.saveDataAndScore();
                    }
                    
                    return result;
                },
                function (err) {
                    console.error(err);
                }
            );
        };
        this.cloudCoderProcess = function (info) {
            var questionData = questionsSaveData.getQuestionData(currentQuestion.getId());
            var partData = questionData.getPartData(currentPart.getId());
            console.log("Code to cloud coder " + partData.getInput().value);
            var cloudCoderProblem = {
                "language": info.find("language").text(),
                "functionOrMethod": info.find("problemtype").text(),
                "testName": info.find("functionname").text(),
                "code": partData.getInput().value,
                "testCases": []
            };
            info.find("testcase").each(function (e) {
                var testCase = {
                    "input": $(this).find("input").html()
                };
                var output = $(this).find("output").html();
                if (typeof (output) !== "undefined" && output !== null) {
                    testCase.output = output;
                }
                cloudCoderProblem.testCases.push(testCase);
            });

            $.ajax({
                type: "POST",
                url: '/jcourse/repl/rest/process/cloud_coder',
                data: JSON.stringify(cloudCoderProblem),
                contentType: "application/json; charset=utf-8",
                success: function (data) {
                    console.log('Success: ' + data);
                    partData.setCorrect(data.compiled && data.allTestsPassed);
                    if (data.allTestsPassed) {
                        partData.setScore(Number(10));
                        partData.setCorrect(true);
                    } else {
                        partData.setScore(0);
                        partData.setCorrect(false);
                    }
                    var results = ' ';
                    if (data.compiled && data.compilationResult.length == 0) {
                        $.each(data.testResults, function (index, value) {
                            results += '<p>' + value + '</p>';
                        });
                    } else {
                        results = data.testResults[0] + " " + data.compilationResult;
                    }
                    var feedback = {id: "cloud_coder_feedback", content: results};
                    partData.setFeedback(feedback);
                    $('#' + currentQuestion.getId() + '_feedback').append('<div style="display: inline-block;">' + partData.getFeedback().content + '</div>');

                    var styles = null;
                    if (!(data.compiled && data.allTestsPassed)) {
                        styles = {
                            background: '#f4cfc9',
                            borderColor: '#e75d36',
                            display: 'block'
                        };
                    } else {
                        styles = {
                            background: '#ddffdd',
                            borderColor: '#33aa33',
                            display: 'block'
                        };
                    }
                    $('#' + currentQuestion.getId() + '_feedback').css(styles);
                    activityEmbed.saveDataAndScore();

                },
                error: function (data) {
                    console.log('Error with Json call ' + data);
                }
            });
        };
        this.saveDataAndScore = function () {
            var attemptScore = questionsSaveData.getAttemptScore();
            var xml = questionsSaveData.toXML();
            var saveData = (new XMLSerializer()).serializeToString(xml.context);
            console.log("saveDataAndScore() " + saveData);
            // Saves current data before scoring the attempt
            superClient.writeFileRecord('student_save_file', 'text/xml', superClient.currentAttempt, saveData, function (response) {
                console.log("WriteFileRecord Data server response: " + response);
                activityEmbed.scoreAttempt(attemptScore);
            });
        };
        this.scoreAttempt = function (attemptScore) {
            console.log("scoreAttempt() score " + attemptScore);
            superClient.scoreAttempt('percent', attemptScore, function (response) {
                console.log("scoreAttempt() server response " + response);
                superClient.endAttempt(function (endAttemptResponse) {
                    console.log("scoreAttempt() endAttempt server response" + endAttemptResponse);
                    superClient.processStartData(endAttemptResponse);
                    activityEmbed.controls();
                });
            });

            //Log the submit action
            var action = new ActionLog(CommonLogActionNames.SUBMIT_ATTEMPT, superClient.sessionId, superClient.resourceId, superClient.activityGuid, "REPL_ACTIVITY", superClient.timeZone);
            var questionsData = questionsSaveData.getQuestionsData();
            for (var key in questionsData) {
                if (questionsData.hasOwnProperty(key)) {
                    var q = questionsData[key];
                    var supplement = new SupplementLog();
                    supplement.setAction(CommonLogActionNames.SCORE_QUESTION);
                    supplement.setInfoType(q.getId());
                    supplement.setInfo(q.getScore());
                    action.addSupplement(supplement);
                    supplement = new SupplementLog();
                    supplement.setAction(CommonLogActionNames.SCORE_QUESTION);
                    supplement.setInfoType('attempt');
                    supplement.setInfo(superClient.currentAttempt);
                    action.addSupplement(supplement);
                    var partsData = q.getPartsData();
                    for (var key in partsData) {
                        if (partsData.hasOwnProperty(key)) {
                            var p = partsData[key];
                            supplement = new SupplementLog();
                            supplement.setAction(CommonLogActionNames.EVALUATE_RESPONSE);
                            supplement.setInfoType(p.getId());
                            supplement.setInfo("<![CDATA[" + p.getInput().value + "]]>");
                            action.addSupplement(supplement);

                            // Important: allows dashboard tracking
                            supplement = new SupplementLog();
                            supplement.setAction(CommonLogActionNames.EVALUATE_RESPONSE_TRACK);
                            supplement.setSource(q.getId());
                            supplement.setInfoType(p.getId());
                            supplement.setInfo(p.getCorrect());
                            action.addSupplement(supplement);

                            supplement = new SupplementLog();
                            supplement.setAction(CommonLogActionNames.MARK_CORRECT);
                            supplement.setInfoType(q.getId() + '/' + p.getId());
                            supplement.setInfo(p.getCorrect());
                            action.addSupplement(supplement);

                            if (p.getFeedback() !== null) {
                                supplement = new SupplementLog();
                                supplement.setAction(CommonLogActionNames.SET_AUTOMATIC_OUTCOME);
                                supplement.setInfoType(p.getId());
                                supplement.setInfo("<![CDATA[" + p.getFeedback() + "]]>");
                                action.addSupplement(supplement);
                            }
                        }
                    }
                }
            }
            superClient.logAction(action);
        };
        this.hint = function () {
            console.log("hint()");
            if (superClient.isCurrentAttemptCompleted()) {
                return;
            }
            if (typeof (currentPart) === "undefined" || currentPart === null || currentPart.getHints().length === 0) {
                return;
            }

            if (typeof (currentPart.hintCnt) === "undefined" || currentPart.hintCnt === null) {
                currentPart.hintCnt = 0;
            }

            if (currentPart.hintCnt >= currentPart.getHints().length) {
                currentPart.hintCnt = 0;
            }
            var hint = currentPart.getHints()[currentPart.hintCnt];
            currentPart.hintCnt++;
            if (questionsSaveData.getQuestionData(currentQuestion.getId())) {
                var pd = questionsSaveData.getQuestionData(currentQuestion.getId())
                        .getPartData(currentPart.getId());
                if (pd) {
                    pd.setHint(hint);
                }
            }
            var action = new ActionLog(CommonLogActionNames.VIEW_HINT, superClient.sessionId, superClient.resourceId, superClient.activityGuid, "REPL_ACTIVITY", superClient.timeZone);
            var supplement = new SupplementLog();
            supplement.setAction(CommonLogActionNames.VIEW_HINT);
            supplement.setInfoType(currentPart.getId());
            supplement.setInfo("<![CDATA[" + hint.content + "]]>");
            action.addSupplement(supplement);
            var supplement = new SupplementLog();

            // Important: allows dashboard tracking
            supplement.setAction(CommonLogActionNames.VIEW_HINT_TRACK);
            supplement.setSource(currentQuestion.getId());
            supplement.setInfoType(currentPart.getId());
            supplement.setInfo("<![CDATA[" + hint.content + "]]>");
            action.addSupplement(supplement);
            superClient.logAction(action);
            $('#' + currentQuestion.getId() + '_hints').find('.content').children().each(function () {
                $(this).remove();
            });
            $('#' + currentQuestion.getId() + '_hints').find('.content').append('<div style="display: inline-block;">' + hint.content + '</div>');
            $('#' + currentQuestion.getId() + '_hints').css('display', 'block');
        };
        this.nextAttempt = function () {
            console.log("nextAttempt()");
            if (superClient.isCurrentAttemptCompleted()) {
                console.log("nextAttempt() requested");
                superClient.startAttempt(function (startData) {
                    console.log("nextAttempt() startAttempt processed by server");
                    document.location.reload();
                });
            } else {
                document.location.reload();
            }
        };
    }

    var activityEmbed = new ActivityEmbed();
    return activityEmbed;
});
