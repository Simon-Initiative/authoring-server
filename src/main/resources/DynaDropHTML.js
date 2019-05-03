/**
 * DynaDropHTML v1.2
 * 
 * Version History:
 *   1.0: - Bug fixes and add interop with HTML layouts created by Course Author v0.13+
 *   1.1: - Add ability to reassign an already assigned draggable
 *   1.2: - Fix an issue related to restore function and not being able to drop items
 *        - Fix an issue where colors weren't being correctly applied to correct/incorrect
 *          responses 
 * */
define(function() {

    var myMap = function(arr, fn) {
        var a = [];
        for (var i = 0; i < arr.length; i++) {
            a.push(fn(arr[i], i));
        }
        return a;
    }

    var getKey = function(item) {
        for (var name in item) {
            if (name.startsWith('@') || name.startsWith('#')) continue;
            return name;
        }
    }

    var wrapArray = function(objOrArray) {
        if (objOrArray === undefined) {
            return [];
        } else if (objOrArray instanceof Array) {
            return objOrArray;
        } else {
            return [objOrArray];
        }
    }


    var myReduce = function(arr, fn, init) {
        var current = init;
        for (var i = 0; i < arr.length; i++) {
            current = fn(current, arr[i]);
        }
        return current;
    }

    var extractText = function(o) {
        var key = getKey(o);
        if (key === undefined && o['#text'] !== undefined) {
            return o['#text'];
        } else if (o[key]['#text'] !== undefined) {
            return o[key]['#text'];
        } else {
            var s = typeof(o) === 'string' ? o.replace(new RegExp('\n', 'g'), '<br/>') : '';
            console.log(s);
            return s;
        }
    }

    var DragDropWrapper = new Class(
        {
            Implements: [Events],


            layoutFile: null,
            viewPlaceholder: null,
            questionId: null,
            isDisabled: false,
            assessment: null,
            currentState: {},

            initialize: function(){

            },

            initOptions: function(options){

                if(options)
                {
                    this.layoutFile = options.layoutFile;
                    this.viewPlaceholder = options.viewPlaceholder;
                    this.assessmentQuestion = options.assessmentQuestion;
                    this.questionId = options.assessmentQuestion.id;
                    trace("New DragDropWrapper1 " + this.questionId);
                    this.assessment = options.assessmentQuestion;
                }
            },

            convertFlashLayout: function(dragdrop) {

                var o = {};
                o.layoutStyles = {};
                o.targetArea = {};
                o.initiators = {};

                var convertedInitiators = myMap(wrapArray(dragdrop.initiatorGroup.initiator), function(initiator) {

                    var contents = '';

                    if (initiator.span !== undefined) {
                        if (initiator.span['#text'] !== undefined) {
                            contents = initiator.span['#text'];
                        } else if (initiator.span.img !== undefined) {
                            contents = '<img src="' + initiator.span.img['@src'] + '"/>';
                        }
                    } else if (initiator instanceof Array && initiator[0].span !== undefined) {
                        if (initiator[0].span['#text'] !== undefined) {
                            contents = initiator[0].span['#text'];
                        } else if (initiator[0].span.img !== undefined) {
                            contents = '<img src="' + initiator[0].span.img['@src'] + '"/>';
                        }
                    } else if (initiator['#text'] !== undefined) {
                        contents = initiator['#text'].replace(new RegExp('\n', 'g'), '<br/>');
                    }


                    return '<div input_val="' + initiator['@assessmentId'] + '" class="initiator">' + contents + '</div>';
                });

                o.initiators['#cdata'] = myReduce(convertedInitiators, function(c, e) { return c + e; }, '');



                var convertedContentRows = myMap(wrapArray(dragdrop.targetGroup.contentRow), function(row) {

                    var convertedTargets = myMap(wrapArray(row.target), function(t) {
                        return '<td><div input_ref="' + t['@assessmentId'] + '" class="target"></div></td>';
                    });

                    var convertedText = myMap(wrapArray(row.text), function(t) {
                        return '<td>' + extractText(t) + '</td>';
                    });

                    return '<tr>' + myReduce(convertedTargets, function(c, e) { return c + e; }, '')
                        + myReduce(convertedText, function(c, e) { return c + e; }, '') + '</tr>';

                });

                var convertedHeaderRows = myMap(wrapArray(dragdrop.targetGroup.headerRow), function(row) {

                    var convertedText = myMap(wrapArray(row.text), function(t) {
                        return '<th>' + extractText(t) + '</th>';
                    });

                    return '<tr>' + myReduce(convertedText, function(c, e) { return c + e; }, '') + '</tr>';
                });

                var tableContents = myReduce(convertedHeaderRows, function(c, e) { return c + e; }, '') +
                    myReduce(convertedContentRows, function(c, e) { return c + e; }, '');

                o.targetArea['#cdata'] = '<table cellspacing="4" border="0">' + tableContents + '</table>';

                o.layoutStyles['#cdata'] = '<style type="text/css">	td, th { text-align: center; } .target { min-height: 20px; min-width: 40px; background: #ccf; margin: 5px; padding: 2px; } '
                    + '.initiator { display: inline-block; min-height: 20px; min-width: 40px; background: #ffc; text-align: center; padding: 5px; cursor: move; border-style: solid; border-width: 1px; '
                    + 'border-color: black; } .dragdropspacer { height: 50px; } </style>';

                return o;
            },

            process: function(){
                if(this.viewPlaceholder)
                {
                    var instance = this;

                    var xml = this.loadXml(this.layoutFile);

                    this.removeComments(xml);

                    var json = xml2json(xml, "");
                    var obj = JSON.decode(json);

                    var dragdrop = null;
                    if (obj.dragdrop.layoutStyles === undefined) {
                        dragdrop = this.convertFlashLayout(obj.dragdrop);
                    } else {
                        dragdrop = obj.dragdrop;
                    }
                    this.renderLayout(dragdrop);

                    this.dispatchEvent(ComponentEvent.COMPONENT_LOADED, {
                        "questionId": instance.questionId
                    });
                }
            },

            loadXml: function (dname)
            {
                var xhttp;

                if (window.XMLHttpRequest)
                {
                    xhttp=new XMLHttpRequest();
                }
                else
                {
                    xhttp=new ActiveXObject("Microsoft.XMLHTTP");
                }

                xhttp.open("GET",dname,false);
                xhttp.send();

                return xhttp.responseXML;
            },

            renderLayout: function(obj)
            {
                var instance = this;
                var componentContainer = new Element("div", {html: obj.layoutStyles["#cdata"]});
                this.viewPlaceholder.grab(componentContainer);
                var targetArea = new Element("div", {
                    html: obj.targetArea["#cdata"],
                    "class": "component"
                });

                targetArea.addEvent("click", function (e) {
                    trace("Click target area " + e.target);
                    if(e.target.get("input_ref")){
                        instance.changeFocus(e.target.get("input_ref"));
                    }else if(e.target.getParent().get("input_ref")){
                        instance.changeFocus(e.target.getParent().get("input_ref"));
                    }
                });

                componentContainer.grab(targetArea);
                var initiatorsContainer = new Element("div", {
                    "class": "input_source",
                    html: obj.initiators["#cdata"]
                });

                // shuffle initiators using Fisher-Yates https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle
                for (var i = initiatorsContainer.children.length; i >= 0; i--) {
                    initiatorsContainer.appendChild(initiatorsContainer.children[Math.random() * i | 0]);
                }

                this.initiatorsContainer = initiatorsContainer;

                componentContainer.grab(initiatorsContainer);

                $$("#" + instance.questionId + ".question .initiator").makeDraggable(
                    {

                        droppables: $$("#" + instance.questionId + ".question .target"),

                        onDrop: function(draggable, droppable)
                        {
                            trace("On Drop");
                            draggable.setStyles(
                                {
                                    "left": "0px",
                                    "top": "0px",
                                    "position": "relative"
                                });

                            if(instance.isDisabled) {
                                return;
                            }

                            if(droppable && droppable.getChildren().length === 0)
                            {
                                droppable.grab(draggable, "top");
                                instance.processUserInput(droppable.get("input_ref"), draggable.get("input_val"));
                                instance.changeFocus(draggable.get("input_val"));
                            }
                            else if (droppable && droppable.getChildren().length === 1) {
                                draggable.getParent().erase('style');
                                initiatorsContainer.grab(droppable.getChildren()[0]);
                                droppable.grab(draggable, "top");
                                instance.processUserInput(droppable.get("input_ref"), draggable.get("input_val"));
                                instance.changeFocus(draggable.get("input_val"));
                            }
                            else
                            {
                                draggable.getParent().erase('style');
                                initiatorsContainer.grab(draggable);
                            }
                        },

                        onDrag: function(el)
                        {
                            trace("On Drag");
                            el.setStyle("z-index", 555);
                        }

                    });

                // This is a temporary fix to keep the initiators seperated.  MooTools makes them absolute when draggable
                $$("#" + instance.questionId + ".question .initiator").setStyles(
                    {
                        position: "relative",
                        left: "0px",
                        top: "0px"
                    });
            },

            processUserInput: function(value, partId)
            {
                var instance = this;

                this.currentState = {
                    [partId]: value,
                    [value]: partId,
                };

                this.dispatchEvent(ComponentEvent.VALUE_COMMIT, {
                    "questionId": instance.questionId,
                    "partId": partId,
                    "partValue": value
                });
            },

            removeComments: function(node) {
                var toRemove = [];
                for (var i = 0; i < node.childNodes.length; i++) {
                    var child = node.childNodes[i];
                    if (child.nodeType === 8) { // Comment type
                        toRemove.push(child);
                    } else {
                        this.removeComments(child);
                    }
                }

                for (var i = 0; i < toRemove.length; i++) {
                    node.removeChild(toRemove[i]);
                }
            },

            changeFocus: function (partId)
            {
                trace("Drag and drop changeFocus() " + partId);

                var instance = this;
                instance.viewPlaceholder.getElements('div[input_ref]')
                    .forEach(function (el) {
                        trace("For Each " +el);
                        el.setStyle("border-width", "1px");
                    });

                var droppable = this.viewPlaceholder.getElement('div[input_ref="' + partId + '"]');

                if (droppable !== null) {
                    droppable.setStyle("border-width", "3px");

                    partId = this.currentState[partId];
                }

                instance.dispatchEvent(ComponentEvent.FOCUS_CHANGE, {
                    "questionId": instance.questionId,
                    "partId": partId
                });

            },

            restore: function (inputVal, inputRef, isCorrect)
            {
                trace("Drag and drop restore()");

                var droppable = this.viewPlaceholder.getElement('div[input_ref="' + inputRef + '"]');
                var draggable = this.viewPlaceholder.getElement('div[input_val="' + inputVal + '"]');

                if (droppable.getChildren().length > 0) {
                    this.initiatorsContainer.grab(droppable.getChildren()[0]);
                }

                droppable.grab(draggable, "top");

                this.currentState = {
                    [inputRef]: inputVal,
                    [inputVal]: inputRef,
                };

                this.correct(inputVal, isCorrect);
            },

            correct: function (value, isCorrect)
            {
                trace("Drag and drop correct() " + value);

                inputRef = this.currentState[value];

                var droppable = this.viewPlaceholder.getElement('div[input_ref="' + inputRef + '"]');

                droppable && droppable.setStyles(
                    {
                        "background-color": isCorrect ? "#ddffdd" : "#f4cfc9"
                    });
            },

            highlight: function (id, isHighlighted)
            {
                trace("Drag and drop highlight()");

                var selector = 'div[input_val]';

                this.viewPlaceholder.getElements(selector)
                    .forEach(function (el) {
                        trace("For Each " +el);
                        el.setStyle("background-color", "");
                    });

                var item = 'div[input_val="' + id + '"]';

                var droppable = this.viewPlaceholder.getElement(item);
                var backgroundColor = isHighlighted ? "cyan" : "none";

                droppable.setStyle("background-color", backgroundColor);

            },

            disable: function (id, isDisabled)
            {
                trace("Drag and drop disable()");
                this.isDisabled = isDisabled;

            },
            // Allows dispatching of all events with optional parameters. A reference to this component as "target" is always included
            dispatchEvent: function (eventType, params) {
                if (!params) {
                    params = {};
                }

                if (!params.target) {
                    params['target'] = this;
                }

                this.fireEvent(eventType, params);
            },

        });
    return DragDropWrapper;
});