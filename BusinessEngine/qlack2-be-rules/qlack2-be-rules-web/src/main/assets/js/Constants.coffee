angular.module("rules")
	.constant("SERVICES", {
		_PREFIX: "/api/apps/rules"

		PROJECTS: "/projects"
		PROJECTS_RECENT: "/projects/recent"
		RESOURCES: "/resources"
		_PROJECTS_SORT: "lastAccessedOn"
		_PROJECTS_ORDER: "desc"
		_PROJECTS_START: "0"
		_PROJECTS_SIZE: "4"
		CATEGORIES: "/categories"

		WORKING_SETS: "/working-sets"
		WORKING_SET_VERSIONS: "/working-set-versions"
		RULES: "/rules"
		RULE_VERSIONS: "/rule-versions"
		DATA_MODELS: "/data-models"
		DATA_MODEL_VERSIONS: "/data-model-versions"
		LIBRARIES: "/libraries"
		LIBRARY_VERSION: "/library-versions"

		FIELD_TYPES: "/field-types"
		DATA_MODELS_JAR: "/models.jar"

		VERSIONS: "/versions"
		VERSION_ACTION_LOCK: "/lock"
		VERSION_ACTION_UNLOCK: "/unlock"
		VERSION_ACTION_ENABLE_TESTING: "/enableTesting"
		VERSION_ACTION_DISABLE_TESTING: "/disableTesting"
		VERSION_ACTION_FINALIZE: "/finalize"
		VERSION_ACTION_EXPORT: "/export"
		VERSION_ACTION_IMPORT: "/import-version"

		CAN_GET: "/canGet"
		CAN_UPDATE_ENABLED_FOR_TESTING: "/canUpdateEnabledForTesting"
		CAN_DELETE: "/canDelete"
		CAN_FINALIZE: "/canFinalize"
		CAN_ENABLE_TESTING: "/canEnableTesting"
		CAN_DISABLE_TESTING: "/canDisableTesting"
		COUNT_LOCKED_BY_OTHER_USER: "/countLockedByOtherUser"

		RUNTIME: "/runtime"
		KNOWLEDGE_BASES: "/knowledge-bases"
		STATELESS_SESSIONS: "/stateless-sessions"

		CONFIG: "/config"
		CONFIG_OPERATIONS: "/operations"
		CONFIG_USERS: "/users"
		CONFIG_GROUPS: "/groups"
		CONFIG_DOMAINS: "/domains"
		CONFIG_MANAGED_USERS: "/users/managed"
		CONFIG_MANAGED_GROUPS: "/groups/managed"
		CONFIG_SUBJECTS: "/subjects"
    })
