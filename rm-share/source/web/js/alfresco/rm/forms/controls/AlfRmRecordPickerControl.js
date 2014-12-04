/**
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

define(["dojo/_base/declare",
        "dojo/_base/lang",
        "dojo/dom",
        "dojo/dom-construct",
        "alfresco/core/CoreWidgetProcessing",
        "alfresco/forms/controls/BaseFormControl"],
        function(declare, lang, dom, domConstruct, CoreWidgetProcessing, BaseFormControl) {

   return declare([CoreWidgetProcessing, BaseFormControl], {

      pickerRootNode: null,

      selectedItem: null,

      site: null,

      constructor: function alfresco_forms_controls_BaseFormControl__constructor(args) {
         declare.safeMixin(this, args);

         this.alfSubscribe("ALF_RECORD_SELECTED", lang.hitch(this, "onRecordSelected"), true);
         this.alfSubscribe("ALF_RECORD_REMOVED", lang.hitch(this, "onRecordRemoved"), true);
      },

      getWidgetConfig: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__getWidgetConfig() {
         return {
            id : this.generateUuid(),
            name: this.name,
            value: this.value
         };
      },

      createFormControl: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__createFormControl(config, domNode) {

         this.itemSelectionPubSubScope = this.generateUuid();

         this.lastValue = null;

         var widgetsForControl = [{
            name: "alfresco/buttons/AlfButton",
            config: {
               label: this.message("label.button.select-record"),
               publishTopic: "ALF_CREATE_DIALOG_REQUEST",
               publishPayload: {
                  dialogTitle: "picker.select.title",
                  handleOverflow: false,
                  widgetsContent: [{
                     name: "alfresco/layout/VerticalWidgets",
                     config: {
                        widgets: [{
                           name: "alfresco/buttons/AlfButton",
                           config: {
                              additionalCssClasses: "relationshipPickerParentNav",
                              publishTopic: "ALF_DOCLIST_PARENT_NAV",
                              showLabel: false,
                              iconClass: "alf-folder-up-icon",
                              disableOnInvalidControls: true
                           }
                        },{
                           name: "alfresco/pickers/Picker",
                           config: {
                              pubSubScope: this.itemSelectionPubSubScope,
                              subPickersLabel: "",
                              widgetsForPickedItems: [{
                                 name: "alfresco/pickers/PickedItems",
                                 assignTo: "pickedItemsWidget",
                                 config: {
                                    singleItemMode: true
                                 }
                              }],
                              widgetsForRootPicker: [{
                                 name: "alfresco/menus/AlfVerticalMenuBar",
                                 config: {
                                    visibilityConfig: {
                                       initialValue: false
                                    },
                                    widgets: [{
                                       name: "alfresco/menus/AlfMenuBarItem",
                                       config: {
                                          publishTopic: "ALF_ADD_PICKER",
                                          publishOnRender: true,
                                          publishPayload: {
                                             currentPickerDepth: 1,
                                             picker: [{
                                                name: "alfresco/pickers/DocumentListPicker",
                                                config: {
                                                   nodeRef: this.pickerRootNode
                                                }
                                             }]
                                          }
                                       }
                                    }]
                                 }
                              }]
                           }
                        }]
                     }
                  }],
                  widgetsButtons: [{
                     name: "alfresco/buttons/AlfButton",
                     config: {
                        label: "picker.ok.label",
                        publishTopic: "ALF_RECORD_SELECTED"
                     }
                  },{
                     name: "alfresco/buttons/AlfButton",
                     config: {
                        label: "picker.cancel.label",
                           publishTopic: "NO_OP"
                     }
                  }]
               },
               publishGlobal: true
            }
         }];

         return this.processWidgets(widgetsForControl, this._controlNode);
      },

      setupChangeEvents: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__setupChangeEvents() {
         this.alfSubscribe(this.itemSelectionPubSubScope + "ALF_ITEMS_SELECTED", lang.hitch(this, this.onItemsSelected), true);
      },

      onItemsSelected: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__onItemsSelected(payload) {
         if (payload.pickedItems.length === 1)
         {
            this.selectedItem = payload.pickedItems[0];
            this.value = lang.clone(this.selectedItem.nodeRef);
            this.onValueChangeEvent(this.name, this.lastValue, this.value);
            this.lastValue = this.value;
         }
      },

      processValidationRules: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__processValidationRules() {
         var valid = true;
         if (this._required === true && (!this.value || this.value.length === 0))
         {
            valid = false;
         }
         return valid;
      },

      getValue: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__getValue() {
         return this.value;
      },

      onRecordSelected: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__onRecordSelected(payload)
      {
         if (dom.byId("alfresco_rm_forms_controls_AlfRmRecordPickerControl"))
         {
            domConstruct.destroy("alfresco_rm_forms_controls_AlfRmRecordPickerControl");
         }

         this.processWidgets([{
            name: "alfresco/rm/lists/AlfRmRelationshipList",
            config: {
               showDeleteAction: true,
               site: this.site,
               currentData: {
                  items: [this.selectedItem]
               }
            }
         }], domConstruct.create("div", {id: "alfresco_rm_forms_controls_AlfRmRecordPickerControl"}, this.containerNode.parentElement, "last"));
      },

      onRecordRemoved: function alfresco_rm_forms_controls_AlfRmRecordPickerControl__onRecordRemoved(payload)
      {
         domConstruct.destroy("alfresco_rm_forms_controls_AlfRmRecordPickerControl");
         this.selectedItem = null;
         this.value = null;
         this.onValueChangeEvent(this.name, this.lastValue, this.value);
      }
   });
});